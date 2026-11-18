"""The published Android APK and update policy, stored on a persistent volume."""

import argparse
import fcntl
import hashlib
import os
import sys
from contextlib import contextmanager
from pathlib import Path
from tempfile import NamedTemporaryFile
from typing import BinaryIO

from pydantic import BaseModel, ConfigDict, Field, model_validator

MAX_VERSION_CODE = 2_100_000_000
MAX_APK_BYTES = 250 * 1024 * 1024
CHUNK_SIZE = 128 * 1024


class AndroidRelease(BaseModel):
    model_config = ConfigDict(strict=True, extra="forbid")

    version_code: int = Field(ge=1, le=MAX_VERSION_CODE)
    version_name: str = Field(pattern=r"^[0-9]+\.[0-9]+\.[0-9]+$", max_length=32)
    size_bytes: int = Field(ge=1, le=MAX_APK_BYTES)
    sha256: str = Field(pattern=r"^[a-f0-9]{64}$")

    @property
    def filename(self) -> str:
        return f"selfcall-{self.version_code}.apk"


class AndroidPolicy(BaseModel):
    minimum_version_code: int = Field(default=0, ge=0, le=MAX_VERSION_CODE)
    latest: AndroidRelease | None = None

    @model_validator(mode="after")
    def check_minimum(self):
        if self.minimum_version_code > (self.latest.version_code if self.latest else 0):
            raise ValueError("Минимальная сборка не может быть выше опубликованной")
        return self


class AndroidReleases:
    def __init__(self, directory: Path):
        self.directory = directory
        self.policy_path = directory / "policy.json"

    def policy(self) -> AndroidPolicy:
        try:
            data = self.policy_path.read_bytes()
        except FileNotFoundError:
            return AndroidPolicy()
        return AndroidPolicy.model_validate_json(data)

    @contextmanager
    def _lock(self, mode: int):
        with (self.directory / ".lock").open("a") as lock:
            fcntl.flock(lock, mode)
            yield

    def _save(self, policy: AndroidPolicy) -> None:
        with NamedTemporaryFile(dir=self.directory, prefix=".policy-", delete=False) as output:
            path = Path(output.name)
            try:
                output.write(policy.model_dump_json().encode())
                output.flush()
                os.fsync(output.fileno())
                path.replace(self.policy_path)
            finally:
                path.unlink(missing_ok=True)

    def download(self, version_code: int) -> tuple[AndroidRelease, BinaryIO] | None:
        if not self.directory.is_dir():
            return None
        # Open the file while locked. A concurrent publication can unlink it once
        # opened without interrupting a response that is already streaming.
        with self._lock(fcntl.LOCK_SH):
            latest = self.policy().latest
            if latest is None or latest.version_code != version_code:
                return None
            file = (self.directory / latest.filename).open("rb")
            if os.fstat(file.fileno()).st_size != latest.size_bytes:
                file.close()
                raise OSError("APK size differs from its published metadata")
            return latest, file

    def publish(self, release: AndroidRelease, stream: BinaryIO) -> AndroidPolicy:
        self.directory.mkdir(parents=True, exist_ok=True)
        digest = hashlib.sha256()
        with NamedTemporaryFile(dir=self.directory, prefix=".upload-", delete=False) as output:
            staged = Path(output.name)
            try:
                remaining = release.size_bytes
                while remaining:
                    chunk = stream.read(min(CHUNK_SIZE, remaining))
                    if not chunk:
                        raise ValueError("Не удалось получить APK целиком")
                    remaining -= len(chunk)
                    digest.update(chunk)
                    output.write(chunk)
                if stream.read(1) or digest.hexdigest() != release.sha256:
                    raise ValueError("Размер или контрольная сумма APK не совпадает")
                output.flush()
                os.fsync(output.fileno())

                with self._lock(fcntl.LOCK_EX):
                    current = self.policy()
                    if current.latest and release != current.latest and release.version_code <= current.latest.version_code:
                        raise ValueError("Номер сборки должен быть выше опубликованного")
                    path = self.directory / release.filename
                    policy = AndroidPolicy(latest=release, minimum_version_code=current.minimum_version_code)
                    staged.replace(path)
                    self._save(policy)
                    for old in self.directory.glob("selfcall-*.apk"):
                        if old != path:
                            old.unlink(missing_ok=True)
                    return policy
            finally:
                staged.unlink(missing_ok=True)

    def set_minimum(self, version_code: int) -> AndroidPolicy:
        self.directory.mkdir(parents=True, exist_ok=True)
        with self._lock(fcntl.LOCK_EX):
            policy = AndroidPolicy(latest=self.policy().latest, minimum_version_code=version_code)
            if version_code and policy.latest:
                if (self.directory / policy.latest.filename).stat().st_size != policy.latest.size_bytes:
                    raise ValueError("Опубликованный APK недоступен")
            self._save(policy)
            return policy


def main() -> None:
    parser = argparse.ArgumentParser(description="Управление выпусками SelfCall для Android")
    actions = parser.add_subparsers(dest="action", required=True)
    actions.add_parser("status", help="показать опубликованную и минимальную сборки")
    actions.add_parser("publish", help="принять метаданные и проверенный APK из stdin")
    minimum = actions.add_parser("minimum", help="изменить минимальную сборку")
    minimum.add_argument("version_code", type=int, help="0 снимает обязательное обновление")
    args = parser.parse_args()
    directory = os.environ.get("ANDROID_RELEASES_DIR")
    if not directory:
        parser.exit(1, "Задайте ANDROID_RELEASES_DIR\n")
    releases = AndroidReleases(Path(directory))
    try:
        if args.action == "publish":
            stream = sys.stdin.buffer
            release = AndroidRelease.model_validate_json(stream.readline(4096))
            result = releases.publish(release, stream)
        elif args.action == "minimum":
            result = releases.set_minimum(args.version_code)
        else:
            result = releases.policy()
    except (OSError, ValueError) as error:
        parser.exit(1, f"{error}\n")
    print(result.model_dump_json())


if __name__ == "__main__":
    main()
