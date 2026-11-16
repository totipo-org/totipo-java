## v0.3.2

Compatibility patch for Android API 30–33. Both `totipo-core` and
`totipo-storage-nio` advance together to 0.3.2.

- Replace newer Java library calls in the shared runtime with API 30 compatible
  hex encoding, bounded stream reads, unsigned identifier comparison, immutable
  collection construction and byte-buffer writes.
- Use a compatible ZIP timestamp setter while preserving profile-1 backup archive
  bytes, including the fixed DOS timestamp and all three frozen golden fixtures.
- Preserve existing application and provider APIs, vault behavior, exact v1/r19
  specification pin and backup archive compatibility. Java 17 bytecode and Bouncy
  Castle 1.86 as core's sole external runtime dependency remain unchanged. No
  Android dependency or separate Android artifact is introduced.

The committed [compatibility report](JAVA_ANDROID_API30_COMPAT_REPORT.md) records
JVM regression coverage and a bounded API 30 reference audit of production classes
after D8 lowering. It does not establish Android execution or physical-device
qualification. Android applications still need API 30–33 and current-device
qualification, including crypto, backup/restore, ZIP behavior and provider I/O.

Release preparation results and outstanding gates are recorded in
[the preparation report](JAVA_0_3_2_RELEASE_PREPARATION_REPORT.md). The protected
release workflow independently qualifies the exact reviewed commit; these notes
do not claim publication.
