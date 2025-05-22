import re
import requests
from datetime import timedelta
import sys

API_KEY = ""
API_URL = "https://www.googleapis.com/youtube/v3/playlistItems"
VIDEO_DETAILS_URL = "https://www.googleapis.com/youtube/v3/videos"


def extract_playlist_id(url):
    match = re.search(r"[?&]list=([a-zA-Z0-9_-]+)", url)
    return match.group(1) if match else ""


def fetch_playlist_items(playlist_id):
    video_ids = []
    next_page_token = ""
    while True:
        params = {
            "part": "contentDetails",
            "playlistId": playlist_id,
            "maxResults": 50,
            "pageToken": next_page_token,
            "key": API_KEY,
        }
        response = requests.get(API_URL, params=params)
        if response.status_code != 200:
            raise RuntimeError("failed to fetch playlist items")

        data = response.json()
        items = data.get("items", [])
        for item in items:
            video_ids.append(item["contentDetails"]["videoId"])

        next_page_token = data.get("nextPageToken", "")
        if not next_page_token:
            break
    return video_ids


def parse_iso8601_duration(duration):
    pattern = re.compile(r"PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?")
    match = pattern.match(duration)
    if not match:
        return timedelta(0)

    hours = int(match.group(1)) if match.group(1) else 0
    minutes = int(match.group(2)) if match.group(2) else 0
    seconds = int(match.group(3)) if match.group(3) else 0

    return timedelta(hours=hours, minutes=minutes, seconds=seconds)


def fetch_video_duration(video_id):
    params = {"part": "contentDetails", "id": video_id, "key": API_KEY}
    response = requests.get(VIDEO_DETAILS_URL, params=params)
    if response.status_code != 200:
        raise RuntimeError("failed to fetch video details")

    items = response.json().get("items", [])
    if not items:
        raise RuntimeError(f"no video details found for ID {video_id}")

    duration_str = items[0]["contentDetails"]["duration"]
    return parse_iso8601_duration(duration_str)


def format_duration(duration):
    total_seconds = int(duration.total_seconds())
    days, remainder = divmod(total_seconds, 86400)
    hours, remainder = divmod(remainder, 3600)
    minutes, seconds = divmod(remainder, 60)

    parts = []
    if days:
        parts.append(f"{days}d")
    if hours or days:
        parts.append(f"{hours}h")
    if minutes or hours or days:
        parts.append(f"{minutes}m")
    parts.append(f"{seconds}s")

    return " ".join(parts)


def print_duration_at_speed(duration, speed):
    adjusted = timedelta(seconds=duration.total_seconds() / speed)
    print(f"At {speed:.2f}x: {format_duration(adjusted)}")


def main():
    if len(sys.argv) < 2:
        print("usage: uv run . <playlist_url>")
        return

    playlist_url = sys.argv[1]
    playlist_id = extract_playlist_id(playlist_url)

    if not playlist_id:
        print("invalid playlist URL.")
        return

    print(f"\nfetching playlist: {playlist_id}...\n")

    try:
        video_ids = fetch_playlist_items(playlist_id)
        total_duration = timedelta()

        for video_id in video_ids:
            try:
                total_duration += fetch_video_duration(video_id)
            except Exception as e:
                print(f"error fetching duration for {video_id}: {e}")

        print("\ntotal duration:", format_duration(total_duration), "\n")
        for speed in [1.25, 1.5, 1.75, 2.0]:
            print_duration_at_speed(total_duration, speed)

    except Exception as e:
        print(f"Error: {e}")


if __name__ == "__main__":
    main()
