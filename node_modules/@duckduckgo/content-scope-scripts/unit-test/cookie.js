import { Cookie } from '../src/cookie.js'

describe('Cookie', () => {
    describe('constructor', () => {
        it('should parse a weird but valid cookie', () => {
            const cki = new Cookie('foo=bar=bar&foo=foo&John=Doe&Doe=John; Max-Age=1000; Domain=.example.com; Path=/; HttpOnly; Secure')
            expect(cki.name).toEqual('foo')
            expect(cki.value).toEqual('bar=bar&foo=foo&John=Doe&Doe=John')
            expect(cki.maxAge).toEqual('1000')
            expect(cki.domain).toEqual('.example.com')
        })
    })

    describe('.getExpiry', () => {
        it('cookie expires in the past', () => {
            const cki = new Cookie('jsdata=; expires=Thu, 01-Jan-1970 00:00:01 GMT; domain=good.third-party.site ;path=/privacy-protections/storage-blocking/iframe.html')
            expect(cki.getExpiry()).toBeLessThan(0)
        })
        it('cookie expires in the future', () => {
            const expectedExpiry = (new Date('Wed, 21 Aug 2030 20:00:00 UTC') - new Date()) / 1000
            const cki = new Cookie('jsdata=783; expires= Wed, 21 Aug 2030 20:00:00 UTC; Secure; SameSite=Lax')
            expect(cki.getExpiry()).toBeCloseTo(expectedExpiry, 0)
        })
        it('cookie with max-age', () => {
            const cki = new Cookie('jsdata=783; expires= Wed, 21 Aug 2030 20:00:00 UTC; Secure; SameSite=Lax; max-age=100')
            expect(cki.getExpiry()).toBe(100)
        })
        it('session cookie', () => {
            const cki = new Cookie('jsdata=783')
            expect(cki.getExpiry()).toBeNaN()
        })
        it('cookie with invalid date expiry', () => {
            const cki = new Cookie('jsdata=783; expires= Wed, 40 Aug 2030 20:00:00 UTC; Secure; SameSite=Lax')
            expect(cki.getExpiry()).toBeNaN()
        })
        it('cookie with invalid max-age expiry', () => {
            const cki = new Cookie('jsdata=783; Secure; SameSite=Lax; max-age=number')
            expect(cki.getExpiry()).toBeNaN()
        })
    })

    describe('maxAge setter', () => {
        it('modifies cookie expiry', () => {
            const cki = new Cookie('jsdata=; expires=Thu, 01-Jan-1970 00:00:01 GMT; domain=good.third-party.site ;path=/privacy-protections/storage-blocking/iframe.html')
            expect(cki.getExpiry()).toBeLessThan(0)
            cki.maxAge = 100
            expect(cki.getExpiry()).toBe(100)
            expect(cki.toString().indexOf('max-age=100')).not.toBe(-1)
        })
        it('updates existing max-age', () => {
            const cki = new Cookie('jsdata=; expires=Thu, 01-Jan-1970 00:00:01 GMT; domain=good.third-party.site ;max-age=50')
            expect(cki.getExpiry()).toBe(50)
            cki.maxAge = 100
            expect(cki.getExpiry()).toBe(100)
            expect(cki.toString().indexOf('max-age=100')).not.toBe(-1)
        })
    })
})
