//
//  ServerTrustViewModel.swift
//
//  Copyright © 2021 DuckDuckGo. All rights reserved.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

import Foundation

struct ServerTrustViewModel: Encodable {

    struct SecCertificateViewModel: Encodable {

        let summary: String?
        let commonName: String?
        let emails: [String]?
        let publicKey: SecKeyViewModel?

        init(secCertificate: SecCertificate) {
            summary = SecCertificateCopySubjectSummary(secCertificate) as String? ?? ""

            var commonName: CFString?
            SecCertificateCopyCommonName(secCertificate, &commonName)
            self.commonName = commonName as String? ?? ""

            var emails: CFArray?
            if errSecSuccess == SecCertificateCopyEmailAddresses(secCertificate, &emails) {
                self.emails = emails as? [String]
            } else {
                self.emails = nil
            }

            var secTrust: SecTrust?
            if errSecSuccess == SecTrustCreateWithCertificates(secCertificate, SecPolicyCreateBasicX509(), &secTrust), let certTrust = secTrust {
                publicKey = SecKeyViewModel(secKey: SecTrustCopyPublicKey(certTrust))
            } else {
                publicKey = nil
            }
        }

    }

    struct SecKeyViewModel: Encodable {

        static func typeToString(_ type: String) -> String? {
            switch type as CFString {
            case kSecAttrKeyTypeRSA: return "RSA"
            case kSecAttrKeyTypeEC: return "Elliptic Curve"
            case kSecAttrKeyTypeECSECPrimeRandom: return "Elliptic Curve (Prime Random)"
            default: return nil
            }
        }

        let keyId: Data?
        let externalRepresentation: Data?

        let bitSize: Int?
        let blockSize: Int?
        let effectiveSize: Int?

        let canDecrypt: Bool
        let canDerive: Bool
        let canEncrypt: Bool
        let canSign: Bool
        let canUnwrap: Bool
        let canVerify: Bool
        let canWrap: Bool

        let isPermanent: Bool?
        let type: String?

        init?(secKey: SecKey?) {
            guard let secKey = secKey else {
                return nil
            }

            blockSize = SecKeyGetBlockSize(secKey)
            externalRepresentation = SecKeyCopyExternalRepresentation(secKey, nil) as Data?

            let attrs: NSDictionary? = SecKeyCopyAttributes(secKey)

            bitSize = attrs?[kSecAttrKeySizeInBits] as? Int
            effectiveSize = attrs?[kSecAttrEffectiveKeySize] as? Int
            canDecrypt = attrs?[kSecAttrCanDecrypt] as? Bool ?? false
            canDerive = attrs?[kSecAttrCanDerive] as? Bool ?? false
            canEncrypt = attrs?[kSecAttrCanEncrypt] as? Bool ?? false
            canSign = attrs?[kSecAttrCanSign] as? Bool ?? false
            canUnwrap = attrs?[kSecAttrCanUnwrap] as? Bool ?? false
            canVerify = attrs?[kSecAttrCanVerify] as? Bool ?? false
            canWrap = attrs?[kSecAttrCanWrap] as? Bool ?? false
            isPermanent = attrs?[kSecAttrIsPermanent] as? Bool ?? false
            keyId = attrs?[kSecAttrApplicationLabel] as? Data

            if let type = attrs?[kSecAttrType] as? String {
                self.type = Self.typeToString(type)
            } else {
                self.type = nil
            }

        }

    }

    let secCertificateViewModels: [SecCertificateViewModel]

    init?(serverTrust: ServerTrust?) {
        guard let serverTrust = serverTrust else {
            return nil
        }

        let secTrust = serverTrust.secTrust
        let count = SecTrustGetCertificateCount(secTrust)
        guard count != 0 else { return nil }

        var secCertificateViewModels = [SecCertificateViewModel]()
        for i in 0 ..< count {
            guard let certificate = SecTrustGetCertificateAtIndex(secTrust, i) else { return nil }
            let certificateViewModel = SecCertificateViewModel(secCertificate: certificate)
            secCertificateViewModels.append(certificateViewModel)
        }

        self.secCertificateViewModels = secCertificateViewModels
    }

}
