# Security
**Library for interacting with the Mac OS X Keychain**

> This library currently only implements the necessary commands for password management for [Cupertino](https://github.com/mattt/cupertino). As such, some methods are stubbed out to raise `NotImplementedError`.

## Usage

```ruby
Security::Keychain::default_keychain.filename #=> "/Users/johnnyappleseed/Library/Keychains/login.keychain"

Security::InternetPassword.find(server: "itunesconnect.apple.com").password #=> "p4ssw0rd"
```

## Contact

Mattt Thompson

- http://github.com/mattt
- http://twitter.com/mattt
- m@mattt.me

## License

Security is available under the MIT license. See the LICENSE file for more info.
