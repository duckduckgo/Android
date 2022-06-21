// swift-tools-version:5.3
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "Autofill",
    products: [
        .library(
            name: "Autofill",
            targets: ["Autofill"]),
    ],
    dependencies: [
    ],
    targets: [
        .target(
            name: "Autofill",
            path: "swift-package/Resources/",
            resources: [
                .copy("assets"),
            ]
        ),
    ]
)
