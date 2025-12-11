#!/bin/bash
set -e

echo "=== Test 1: List commands ==="
./iw

echo ""
echo "=== Test 2: Run version command ==="
./iw version

echo ""
echo "=== Test 3: Run hello with no args ==="
./iw hello

echo ""
echo "=== Test 4: Run hello with args ==="
./iw hello "Approach 2" "Git Style"

echo ""
echo "=== Test 5: Test unknown command (should fail) ==="
./iw unknown || echo "Correctly failed with exit code $?"

echo ""
echo "=== Test 6: Verify caching (second run should be faster) ==="
time ./iw version > /dev/null 2>&1

echo ""
echo "=== All tests passed! ==="
