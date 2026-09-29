"""Prints the captions of one YouTube video as "[mm:ss] text" lines (ADR-0004).

Called by the research module with an argument list, never a shell. Exit codes:
0 captions printed, 2 the video has no usable captions, 3 fetching failed or was blocked.
Captions only: no media, no cookies, no proxies (ADR-0002).
"""

import re
import sys

from youtube_transcript_api import YouTubeTranscriptApi
from youtube_transcript_api._errors import NoTranscriptFound, TranscriptsDisabled

VIDEO_ID = re.compile(r"^[A-Za-z0-9_-]{11}$")


def timestamp(seconds):
    whole = int(seconds)
    hours, rest = divmod(whole, 3600)
    minutes, secs = divmod(rest, 60)
    return f"{hours}:{minutes:02d}:{secs:02d}" if hours else f"{minutes:02d}:{secs:02d}"


def main(argv):
    if len(argv) != 2 or not VIDEO_ID.match(argv[1]):
        print("expected one 11-character video ID", file=sys.stderr)
        return 3
    try:
        transcripts = YouTubeTranscriptApi().list(argv[1])
        try:
            transcript = transcripts.find_transcript(["en"])
        except NoTranscriptFound:
            transcript = next(iter(transcripts))
        snippets = transcript.fetch()
    except (NoTranscriptFound, TranscriptsDisabled, StopIteration):
        print("no captions available", file=sys.stderr)
        return 2
    except Exception as error:  # blocked, network, or library breakage
        print(f"fetch failed: {type(error).__name__}", file=sys.stderr)
        return 3
    for snippet in snippets:
        text = " ".join(snippet.text.split())
        if text:
            print(f"[{timestamp(snippet.start)}] {text}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
