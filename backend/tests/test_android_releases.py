from hashlib import sha256
from io import BytesIO

import pytest

from test_api import client, fake  # noqa: F401
from app.android_releases import AndroidRelease, AndroidReleases
from app.config import settings


def release(code, contents):
    return AndroidRelease(
        version_code=code,
        version_name="0.4.0",
        size_bytes=len(contents),
        sha256=sha256(contents).hexdigest(),
    )


def test_publish_is_atomic_and_only_latest_is_downloadable(tmp_path):
    store = AndroidReleases(tmp_path)
    first, next_release = release(5, b"first"), release(6, b"second")
    store.publish(first, BytesIO(b"first"))
    store.set_minimum(5)
    _, open_download = store.download(5)
    with open_download:
        with pytest.raises(ValueError):
            store.publish(next_release, BytesIO(b"broken"))
        with pytest.raises(ValueError):
            store.set_minimum(6)
        assert store.policy().latest == first
        assert store.publish(next_release, BytesIO(b"second")).minimum_version_code == 5
        assert open_download.read() == b"first"
    assert store.download(5) is None
    assert list(tmp_path.glob("*.apk")) == [tmp_path / "selfcall-6.apk"]
    with pytest.raises(ValueError):
        store.publish(first, BytesIO(b"first"))
    with pytest.raises(ValueError):
        store.publish(release(6, b"changed"), BytesIO(b"changed"))
    assert store.publish(next_release, BytesIO(b"second")).latest == next_release


def test_minimum_blocks_old_android_and_leaves_updates_and_web_accessible(client, tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "android_releases_dir", tmp_path)
    android = {"X-SelfCall-Android-Version": "4"}
    metadata = "/api/android/update"
    download = "/api/android/releases/selfcall-5.apk"
    assert client.get(metadata).json() == {"minimum_version_code": 0, "latest": None}

    store = AndroidReleases(tmp_path)
    latest = release(5, b"first")
    store.publish(latest, BytesIO(b"first"))
    assert client.post("/api/meetings", headers=android).status_code == 201

    store.set_minimum(5)
    web = client.post("/api/meetings")
    assert web.status_code == 201
    meeting = f"/api/meetings/{web.json()['id']}"
    assert client.post(f"{meeting}/join", json={}).status_code == 200
    assert client.get(meeting, headers=android).status_code == 426
    blocked = client.post(f"{meeting}/join", json={}, headers={"User-Agent": "okhttp/5.0"})
    assert blocked.status_code == 426
    assert blocked.json()["detail"] == {
        "code": "update_required",
        "message": "Чтобы продолжить, обновите SelfCall.",
        "update": client.get(metadata, headers=android).json(),
    }
    assert client.get(metadata).headers["cache-control"] == "no-store"
    response = client.get(download, headers=android)
    assert response.content == b"first"
    assert response.headers["content-disposition"] == 'attachment; filename="selfcall-5.apk"'
    assert response.headers["content-type"] == "application/vnd.android.package-archive"
    assert client.get("/api/android/releases/selfcall-4.apk").status_code == 404
    assert client.post(f"{meeting}/join", json={}, headers={"X-SelfCall-Android-Version": "5"}).status_code == 200

    store.set_minimum(0)
    assert client.post(f"{meeting}/join", json={}, headers=android).status_code == 200
