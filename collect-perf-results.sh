#!/bin/bash
#
adb logcat -e 'PERF METRICS' | tee perf-results.txt