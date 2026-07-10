#!/bin/sh
# Per-second in-guest CPU sampler for RestQ per-process energy attribution.
#
# Runs inside a container guest; power_collector.py (collect --sample-guests)
# feeds it to `container exec -i <name> sh -s`. It is written in POSIX shell
# and awk only, because the database and Java runtime images ship no Python.
#
# Emits one JSON line per second with cumulative, monotonic per-group work
# counters. The attribute stage takes deltas, so a lost line costs one
# interval and never corrupts the series.
#
#   {"hz": 100}                                       <- header, once
#   {"ts": 1783270000.123, "busy": 12345,
#    "groups": {"backends": 42, "background": 3, "java": 0}}
#
# busy is the count of non-idle jiffies from /proc/stat (total - idle -
# iowait). groups holds the accumulated utime+stime deltas per process
# group (per-PID bookkeeping lives in a state file), classified by
# cmdline/comm:
#
#   backends    postgres workers executing client queries
#               ("postgres: <user> <db> ...", including parallel workers)
#   background  any other postgres process (postmaster, checkpointer,
#               walwriter, autovacuum, stats collector, ...)
#   java        the JVM in the API container
#
# Everything else is guest-other, computed at attribute time as
# busy - sum(groups).
#
# Short-lived children (parallel query workers, one-shot connections) die
# between samples and would escape live sampling. The postmaster's
# cutime+cstime accumulates the full lifetime CPU of every reaped child, so
# backends additionally receives max(0, reaped - already credited to dead
# PIDs). Historical reaps from before the sampler started are a constant
# offset and cancel in the attribute stage's deltas.

hz=$(getconf CLK_TCK 2>/dev/null || echo 100)
printf '{"hz": %s}\n' "$hz"

state=$(mktemp /tmp/guest_sampler_state.XXXXXX 2>/dev/null || echo "/tmp/guest_sampler_state.$$")
: > "$state"

while :; do
  ts=$(date +%s.%3N)
  busy=$(awk '/^cpu /{t=0; for(i=2;i<=NF;i++) t+=$i; print t-$5-$6}' /proc/stat)

  {
    for d in /proc/[0-9]*; do
      pid=${d#/proc/}
      stat=$(cat "$d/stat" 2>/dev/null) || continue
      # The comm field sits in parentheses and may contain spaces, so the
      # line is parsed around the last ')'.
      comm=${stat#*\(}; comm=${comm%%\)*}
      after=${stat##*\) }
      # Field positions after the ')': state is field 1, utime field 12,
      # stime field 13, cutime field 14, cstime field 15.
      set -- $after
      jiffies=$(( ${12} + ${13} ))
      # cat owns the file open: a PID exiting between the stat and cmdline
      # reads must surface as a normal command failure, not as a shell
      # redirection error, which is fatal in dash and would kill the sampler.
      cmdline=$(cat "$d/cmdline" 2>/dev/null | tr '\0' ' ')

      group=""
      # mysqld is a single multi-threaded process (utime/stime are
      # process-wide and it does not fork), so it maps cleanly onto the
      # backends group.
      case "$comm" in java) group=java ;; mysqld) group=backends ;; esac
      if [ -z "$group" ]; then
        case "$cmdline" in
          "postgres: "*)
            word2=${cmdline#postgres: }; word2=${word2%% *}
            case "$word2" in
              checkpointer|background|walwriter|autovacuum|stats|logical|archiver|startup|walreceiver)
                group=background ;;
              *) group=backends ;;
            esac ;;
          postgres*)
            # The postmaster: its cutime+cstime carries the CPU time of its
            # reaped children.
            group=background
            echo "REAP $(( ${14} + ${15} ))" ;;
        esac
      fi
      [ -n "$group" ] && echo "$pid $group $jiffies"
    done
  } | awk -v state="$state" -v ts="$ts" -v busy="$busy" '
    BEGIN {
      while ((getline line < state) > 0) {
        n = split(line, f, " ")
        if (f[1] == "ACC") {
          acc["backends"] = f[2]; acc["background"] = f[3]
          acc["java"] = f[4]; dead = f[5]
        } else { last[f[2]] = f[4]; lgroup[f[2]] = f[3] }
      }
      close(state)
      reap = 0
    }
    $1 == "REAP" { reap = $2; next }
    {
      pid = $1; group = $2; j = $3
      seen[pid] = 1
      prev = (pid in last) ? last[pid] : 0
      # A counter lower than the previous sample means the PID was reused.
      if (j < prev) prev = 0
      acc[group] += j - prev
      newgroup[pid] = group; newlast[pid] = j
    }
    END {
      # Postgres PIDs that vanished already had their sampled portion
      # credited; the reap counter carries their full lifetimes.
      for (pid in last)
        if (!(pid in seen) && lgroup[pid] != "java")
          dead += last[pid]
      extra = reap - dead
      if (extra < 0) extra = 0
      printf "{\"ts\": %s, \"busy\": %s, \"groups\": {\"backends\": %d, \"background\": %d, \"java\": %d}}\n", \
             ts, busy, acc["backends"] + extra, acc["background"] + 0, acc["java"] + 0
      printf "ACC %d %d %d %d\n", acc["backends"] + 0, acc["background"] + 0, acc["java"] + 0, dead + 0 > state
      for (pid in newlast)
        printf "PID %s %s %s\n", pid, newgroup[pid], newlast[pid] >> state
      close(state)
    }
  '
  sleep 1
done
