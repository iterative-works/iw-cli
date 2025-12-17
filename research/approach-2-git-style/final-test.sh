#!/bin/bash
set -e

echo "========================================="
echo "Final Comprehensive Test Suite"
echo "========================================="
echo ""

echo "1. Clean all caches..."
rm -rf iw-*/.scala-build

echo "2. List commands..."
./iw
echo ""

echo "3. Test version command..."
./iw version
echo ""

echo "4. Test hello command (no args)..."
./iw hello
echo ""

echo "5. Test hello command (with args)..."
./iw hello "Approach 2 Prototype"
echo ""

echo "6. Test status command..."
./iw status
echo ""

echo "7. Test caching (run version again)..."
time ./iw version > /dev/null 2>&1
echo ""

echo "8. Verify independent compilation..."
(cd iw-version && scala-cli compile . > /dev/null 2>&1)
echo "   - iw-version compiles independently: OK"
(cd iw-hello && scala-cli compile . > /dev/null 2>&1)
echo "   - iw-hello compiles independently: OK"
(cd iw-status && scala-cli compile . > /dev/null 2>&1)
echo "   - iw-status compiles independently: OK"
echo ""

echo "9. Test unknown command (should fail)..."
if ./iw unknown 2>&1 | grep -q "Error: Unknown command"; then
    echo "   - Error handling works: OK"
else
    echo "   - Error handling FAILED"
    exit 1
fi
echo ""

echo "========================================="
echo "All tests passed successfully!"
echo "========================================="
