# Journal v1 compatibility fixture

`v1-compatibility.bin` was written using the pre-refactor Journal and DurableExchange from commit
`71466732fbf0f0431ef91b5852b3d48d037f168a`, compiled with `javac --release 17`.

Commands: sell limit 1 @100 for 5; sell limit 2 @100 for 7; buy market 3 for 6; buy limit 2 @90 for 1 (rejected ID
reuse).

Expected recovered state: command sequence 4, highest accepted order ID 3, two trades, sell order 2 remaining 6 @100.
The 184-byte fixture includes the original header, wire codes and CRC32C frames. Tests compare new writer output
byte-for-byte and recover/continue from these original bytes.
