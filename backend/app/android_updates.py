import logging

from fastapi import APIRouter, HTTPException, Request, Response
from fastapi.responses import StreamingResponse
from starlette.background import BackgroundTask

from .android_releases import AndroidPolicy, AndroidReleases, CHUNK_SIZE
from .config import settings

log = logging.getLogger("selfcall")
router = APIRouter(prefix="/api/android")
VERSION_HEADER = "X-SelfCall-Android-Version"


def _unavailable(error: Exception) -> HTTPException:
    log.error("Android release store unavailable (%s)", type(error).__name__)
    return HTTPException(503, detail={
        "code": "update_unavailable", "message": "Обновление недоступно. Попробуйте позже.",
    })


def _policy() -> AndroidPolicy:
    try:
        directory = settings.android_releases_dir
        return AndroidReleases(directory).policy() if directory else AndroidPolicy()
    except (OSError, ValueError) as error:
        raise _unavailable(error) from error


@router.get("/update", response_model=AndroidPolicy)
def update(response: Response) -> AndroidPolicy:
    response.headers["Cache-Control"] = "no-store"
    return _policy()


@router.get("/releases/selfcall-{version_code}.apk")
def download(version_code: int) -> StreamingResponse:
    try:
        directory = settings.android_releases_dir
        result = AndroidReleases(directory).download(version_code) if directory else None
    except (OSError, ValueError) as error:
        raise _unavailable(error) from error
    if result is None:
        raise HTTPException(404, detail={
            "code": "release_not_found", "message": "Проверьте, какая версия доступна сейчас.",
        })
    release, file = result
    return StreamingResponse(
        iter(lambda: file.read(CHUNK_SIZE), b""),
        media_type="application/vnd.android.package-archive",
        headers={
            "Content-Disposition": f'attachment; filename="{release.filename}"',
            "Content-Length": str(release.size_bytes),
            "Cache-Control": "private, no-store",
        },
        background=BackgroundTask(file.close),
    )


def require_supported(request: Request, *, android_only: bool = False) -> None:
    raw_version = request.headers.get(VERSION_HEADER)
    # Android releases before 0.4.0 sent the default OkHttp user agent only.
    legacy_android = request.headers.get("user-agent", "").startswith("okhttp/")
    if raw_version is None and not (android_only or legacy_android):
        return
    version = 0
    if raw_version and len(raw_version) <= 10 and raw_version.isascii() and raw_version.isdecimal():
        version = int(raw_version)
    policy = _policy()
    if version < policy.minimum_version_code:
        raise HTTPException(426, detail={
            "code": "update_required",
            "message": "Чтобы продолжить, обновите SelfCall.",
            "update": policy.model_dump(),
        }, headers={"Cache-Control": "no-store"})
