#!/bin/sh
# Stands in for the Python transcript tool; behaviour is chosen by the video ID argument.
case "$1" in
  okVideo0001) printf '[00:01] first line\n[00:05] second line\n' ;;
  noCaptions1) echo "no captions" >&2; exit 2 ;;
  brokenVideo) echo "blocked" >&2; exit 3 ;;
  emptyVideo1) exit 0 ;;
  slowVideo01) sleep 10 ;;
  hugeOutput1) while :; do echo "0123456789012345678901234567890123456789"; done ;;
  *) exit 3 ;;
esac
