#!/bin/sh
# Prints line and branch coverage from JaCoCo's CSV report as a Markdown table:
# the total first, then each class (nested classes counted with their outer class).
#
#   ./gradlew test && sh scripts/coverage-summary.sh
#   sh scripts/coverage-summary.sh build/reports/jacoco/test/jacocoTestReport.csv
#
# CI appends the output to the job summary, so every PR shows its coverage.
# Needs only sh and awk.

CSV="${1:-build/reports/jacoco/test/jacocoTestReport.csv}"

if [ ! -f "$CSV" ]; then
  echo "No coverage report at $CSV; run ./gradlew test first." >&2
  exit 1
fi

awk -F, '
  function pct(covered, missed) {
    return (covered + missed) == 0 ? "-" : sprintf("%.1f%%", 100 * covered / (covered + missed))
  }
  NR == 1 { next }   # header
  {
    cls = $3; sub(/\..*/, "", cls)   # Outer.Inner -> Outer
    bm[cls] += $6; bc[cls] += $7; lm[cls] += $8; lc[cls] += $9
    tbm += $6; tbc += $7; tlm += $8; tlc += $9
  }
  END {
    print "### Unit test coverage"
    print ""
    printf "**Lines: %s** (%d / %d) · **Branches: %s** (%d / %d)\n\n", \
      pct(tlc, tlm), tlc, tlc + tlm, pct(tbc, tbm), tbc, tbc + tbm
    print "| Class | Lines | Branches |"
    print "| - | - | - |"
    n = 0
    for (c in lc) names[++n] = c
    # Sort by name so the table is stable between runs.
    for (i = 2; i <= n; i++) {
      v = names[i]
      for (j = i - 1; j >= 1 && names[j] > v; j--) names[j + 1] = names[j]
      names[j + 1] = v
    }
    for (i = 1; i <= n; i++) {
      c = names[i]
      printf "| `%s` | %s | %s |\n", c, pct(lc[c], lm[c]), pct(bc[c], bm[c])
    }
  }
' "$CSV"
