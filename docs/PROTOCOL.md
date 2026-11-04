# Protocol notes

These are observations and independently implemented interoperability rules, not an official manufacturer specification. Unknown services are enumerated but not probed with extra commands.

## Input

XDS private service `00001828-0000-1000-8000-00805f9b34fb` reuses SIG UUIDs in a private arrangement; do not interpret its `2A63` payload as standard CPS.

Subscribe `2A63` CCCD with `01 00`, then after800ms write `02 16 AA 10` to `2A55`. On the tested device, the write receives ATT application error81 but a measurement arrives about100ms later. An immediate disconnect loses that data. The bridge preserves subscription for up to5s after81; valid measurements continue the stream, otherwise it reconnects. Other failed writes remain fatal unless measurements already arrived. The private meaning of81 remains undetermined; it must not be generalized to other devices.

| Offset | Width | Reference interpretation |
|---|---|---|
| 0 | 2 | total power uint16 little endian |
| 2 | 2 | left power sint16 little endian |
| 4 | 2 | right power sint16 little endian |
| 6 | 2 | cadence sint16 little endian; negative mapped to0 for forward output |
| 8 | 2 | reference angle, shown only within0..359 |
| 10 | 1 | sensor status; nonzero pauses forwarding |

The reference parser accepts truncated packets with at least2 bytes and treats missing fields as unknown. Powers above32767W cannot be represented in standard CPS and are rejected. Tested notifications were11 bytes. All415 sampled bilateral sums matched total power; independent bilateral sensing is not established. Ratios require total>=10W and consistent nonnegative sides.

Read-only extras: Battery180F/2A19, Device Information180A model2A24/firmware2A26/hardware2A27/manufacturer2A29 when present, remote RSSI. Operations serialize after outstanding startup writes. A10s read timeout suspends optional reads until reconnect without tearing down the power stream. No battery subscriptions or new control writes are added.

## Output

CPS1818: Measurement2A63 Notify with CCCD2902; Feature2A65=`08 00 00 00` for crank data; Location2A5D=Other. Measurements contain flags0 + signed watts, or flags0x20 + signed watts + uint16 cumulative crank revolutions + uint16 last crank event time in1/1024s. Crank events are reconstructed by integrating received rpm; timing is estimated rather than native sensor event timing. Stop/missing/expired input must not manufacture further revolutions.

Advertise connectable legacy BLE: primary CPS UUID plus manufacturer company fieldFFFF/data `6B 00 18 C2 05 4C 01 01 01 06 83`; scan-response name `P715 49688`. These were observed together in a GROWTAC P715 screenshot and worked as a combination. The individual discovery criteria and manufacturer-byte semantics are not established; FFFF is not asserted to be Magene's allocated company ID.

The tested C606 Pro writes measurement CCCD `03 00`. Accept that exact compatibility case but continue sending Notify, not Indicate. Standard01 00 and unsubscribe00 00 also work;02 00/unknown bits are rejected. This is an interoperability exception, not a claim that03 00 is required by standard CPS.

Output byte budget, subscription rules, expiry, cadence timing/rollover and startup81 handling have unit checks. Packet captures, device addresses and signing keys are deliberately not part of this repository.

References:
- https://github.com/DuoDuoJuZi/xds_forwarding (`xds_protocol.c`, `xds_ble_bridge.c`)
- https://growtac.com/products/p715-documents/
- https://growtac.com/wp/wp-content/uploads/P715-3-1-1024x552.png
