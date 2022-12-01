# SPDX-License-Identifier: Apache-2.0
#
# Copyright © 2017-2021 WireGuard LLC. All Rights Reserved.

BUILDDIR ?= $(CURDIR)/build
DESTDIR ?= $(CURDIR)/out

NDK_GO_ARCH_MAP_x86 := 386
NDK_GO_ARCH_MAP_x86_64 := amd64
NDK_GO_ARCH_MAP_arm := arm
NDK_GO_ARCH_MAP_arm64 := arm64
NDK_GO_ARCH_MAP_mips := mipsx
NDK_GO_ARCH_MAP_mips64 := mips64x

CLANG_FLAGS := --target=$(ANDROID_LLVM_TRIPLE) --gcc-toolchain=$(ANDROID_TOOLCHAIN_ROOT) --sysroot=$(ANDROID_SYSROOT)
export CGO_CFLAGS := $(CLANG_FLAGS) $(CFLAGS)
export CGO_LDFLAGS := $(CLANG_FLAGS) $(LDFLAGS) -L$(DESTDIR) -lnetguard -Wl,-soname=libwg-go.so
export CC := $(ANDROID_C_COMPILER)
export GOARCH := $(NDK_GO_ARCH_MAP_$(ANDROID_ARCH_NAME))
export GOOS := android
export CGO_ENABLED := 1

default: $(DESTDIR)/libwg-go.so

$(DESTDIR)/libwg-go.so: $(wildcard *) $(wildcard ../netguard/src/*)
	 go build -tags android -ldflags="-X golang.zx2c4.com/wireguard/ipc.socketDirectory=/data/data/com.duckduckgo.mobile.android.debug.netp/cache/wireguard" -v -trimpath -o "$@" -buildmode c-shared

.DELETE_ON_ERROR:
