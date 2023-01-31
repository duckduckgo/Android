#! /bin/sh

CT='ct.c'

echo '#include <assert.h>' > "$CT"
echo '#include <sodium.h>' >> "$CT"
echo 'int main(void) {' >> "$CT"
for macro in $(egrep -r '#define crypto_.*BYTES(_[A-Z]+)? ' src/libsodium/include | \
               cut -d: -f2- | cut -d' ' -f2 | sort -u); do
  func=$(echo "$macro" | tr A-Z a-z)
  echo "    assert($func() == $macro);" >> "$CT"
done
echo "return 0; }" >> "$CT"

CPPFLAGS="${CPPFLAGS} -Wno-deprecated-declarations"
${CC:-cc} "$CT" $CPPFLAGS $CFLAGS $LDFLAGS -lsodium || exit 1
./a.out || exit 1
rm -f a.out "$CT"

