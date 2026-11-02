# Compatibility and limitations

## Android host

Initial target: ARM64 Android devices, with Google Pixel 9 Pro XL as the primary test device.

## Linux

A PRoot-based ARM64 userspace is the first target. PRoot provides userspace filesystem and execution conveniences; it is not a virtual machine and does not provide a separate Linux kernel. Some packages and services may fail due to Android kernel, namespace, syscall, init-system or background-execution differences.

## Windows

Android cannot natively install Windows PE executables. Wine provides a compatibility layer, not a Windows installation. x86/x64 applications additionally need suitable CPU translation. Graphics translation and driver support are device-dependent. The Pixel 9 Pro XL uses a Mali GPU, so Snapdragon/Adreno-specific instructions cannot be assumed to work.

## Virtual machines

QEMU software emulation can be slow and memory intensive. Hardware acceleration availability must be established on the actual device before offering accelerated VMs.

## Validation labels

- **Implemented:** code exists and automated tests pass.
- **Device verified:** tested on a named physical device and OS build.
- **Experimental:** prototype with known compatibility gaps.
- **Planned:** not implemented.
- **Research:** feasibility not yet established.
