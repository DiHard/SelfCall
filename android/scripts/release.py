#!/usr/bin/env python3
"""Build and publish the standalone Android APK from the signing Mac."""

import argparse
import fcntl
import hashlib
import json
import os
import re
import shlex
import shutil
import subprocess
from pathlib import Path
from tempfile import TemporaryDirectory

ANDROID = Path(__file__).resolve().parents[1]
ARCHIVE = ANDROID / "releases"
SIGNING_DIR = Path(os.environ.get(
    "SELFCALL_SIGNING_DIR", Path.home() / "Library/Application Support/SelfCall/android-signing",
))
JAVA_HOME = os.environ.get("JAVA_HOME", "/Applications/Android Studio.app/Contents/jbr/Contents/Home")
TOOLS_ENV = {**os.environ, "JAVA_HOME": JAVA_HOME}


def inspect_apk(apk: Path) -> dict:
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get(
        "ANDROID_SDK_ROOT", Path.home() / "Library/Android/sdk",
    ))
    tools = max(
        (path for path in (sdk / "build-tools").iterdir()
         if re.fullmatch(r"[0-9]+(?:\.[0-9]+)+", path.name)
         and (path / "aapt").is_file() and (path / "apksigner").is_file()),
        key=lambda path: tuple(map(int, path.name.split("."))),
    )
    signature = subprocess.check_output(
        [tools / "apksigner", "verify", "--print-certs", apk], env=TOOLS_ENV, text=True,
    )
    signers = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$", signature, re.M)
    if [signer.lower() for signer in signers] != [(ANDROID / "release-cert.sha256").read_text().strip()]:
        raise ValueError("APK должен быть подписан постоянным ключом SelfCall")

    badging = subprocess.check_output([tools / "aapt", "dump", "badging", apk], text=True)
    package = re.search(
        r"^package: name='ru\.ifx\.selfcall' versionCode='([0-9]+)' versionName='([0-9]+\.[0-9]+\.[0-9]+)'(.*)$",
        badging, re.M,
    )
    min_sdk = re.search(r"^sdkVersion:'([0-9]+)'$", badging, re.M)
    if (not package or "split=" in package[3] or "application-debuggable" in badging
            or not min_sdk or int(min_sdk[1]) > 26):
        raise ValueError("Ожидается APK выпуска ru.ifx.selfcall для всех поддерживаемых устройств (API 26+)")
    with apk.open("rb") as file:
        digest = hashlib.file_digest(file, "sha256").hexdigest()
    return {
        "version_code": int(package[1]),
        "version_name": package[2],
        "size_bytes": apk.stat().st_size,
        "sha256": digest,
    }


def build(offline: bool) -> None:
    if not all((SIGNING_DIR / name).is_file() for name in ("selfcall-release.p12", "signing.properties")):
        raise ValueError("Постоянный ключ подписи и signing.properties должны быть в папке ключа SelfCall")
    version = dict(line.split("=", 1) for line in (ANDROID / "version.properties").read_text().splitlines())
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version["versionName"]):
        raise ValueError("Укажите версию в формате X.Y.Z в android/version.properties")

    ARCHIVE.mkdir(exist_ok=True)
    with (SIGNING_DIR / "build.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        counter = SIGNING_DIR / "last-version-code"
        previous = int(counter.read_text()) if counter.exists() else 0
        code = max(previous + 1, int(version["firstVersionCode"]))
        output = ARCHIVE / f"selfcall-{code}.apk"
        if output.exists():
            raise ValueError(f"Сборка {code} уже есть. Восстановите счётчик last-version-code.")
        # Reserve the number before building, so retries and concurrent builds cannot reuse it.
        with TemporaryDirectory(dir=SIGNING_DIR) as directory:
            reserved = Path(directory) / "version-code"
            reserved.write_text(f"{code}\n")
            reserved.replace(counter)
        command = ["./gradlew", ":app:assembleRelease", f"-PSELFCALL_VERSION_CODE={code}",
                   "-PSELFCALL_SERVER=https://call.gamma.help"]
        if offline:
            command.append("--offline")
        subprocess.run(command, cwd=ANDROID, env=TOOLS_ENV, check=True)
        apk = ANDROID / "app/build/outputs/apk/release/app-release.apk"
        info = inspect_apk(apk)
        if (info["version_code"], info["version_name"]) != (code, version["versionName"]):
            raise ValueError("Версия APK не соответствует зарезервированной сборке")
        with TemporaryDirectory(dir=ARCHIVE) as directory:
            staged = Path(directory) / output.name
            shutil.copyfile(apk, staged)
            staged.replace(output)
    print(output)


def publish(apk: Path) -> None:
    server = os.environ.get("SERVER", "araratadmin@188.164.152.23")
    infra = f'{os.environ.get("REMOTE_DIR", "selfcall")}/infra'
    with TemporaryDirectory() as directory:
        bundle = Path(directory) / "release"
        with bundle.open("wb") as output:
            info = inspect_apk(apk)
            output.write(json.dumps(info).encode() + b"\n")
            with apk.open("rb") as source:
                shutil.copyfileobj(source, output)
        remote = subprocess.check_output(
            ["ssh", "--", server, 'mktemp -d "$HOME/.selfcall-android.XXXXXXXX"'], text=True,
        ).strip()
        if not re.fullmatch(r"/[A-Za-z0-9_./-]+/\.selfcall-android\.[A-Za-z0-9]+", remote):
            raise ValueError("Сервер вернул неожиданный путь для загрузки APK")
        remote_bundle = shlex.quote(f"{remote}/release")
        try:
            subprocess.run(["scp", "--", bundle, f"{server}:{remote}/release"], check=True)
            command = (
                f"cd {shlex.quote(infra)} && sudo docker compose exec -T backend "
                f"python -m app.android_releases publish < {remote_bundle}"
            )
            subprocess.run(["ssh", "-t", "--", server, command], check=True)
        finally:
            subprocess.run(["ssh", "--", server,
                            f"rm -f -- {remote_bundle} && rmdir -- {shlex.quote(remote)}"], check=False)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    builder = commands.add_parser("build", help="собрать APK с новым номером")
    builder.add_argument("--offline", action="store_true", help="взять зависимости Gradle из локального кеша")
    commands.add_parser("publish", help="проверить APK и опубликовать на сервере").add_argument("apk", type=Path)
    commands.add_parser("inspect", help="проверить подпись и показать метаданные APK").add_argument("apk", type=Path)
    args = parser.parse_args()
    try:
        if args.command == "build":
            build(args.offline)
        elif args.command == "publish":
            publish(args.apk)
        else:
            print(json.dumps(inspect_apk(args.apk)))
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
