import { PasswordGenerator } from './PasswordGenerator'

describe('PasswordGenerator', () => {
    it('generates a password once', () => {
        const pwg = new PasswordGenerator()
        expect(pwg.generated).toBe(false)
        const pws = [
            pwg.generate(),
            pwg.generate(),
            pwg.generate(),
            pwg.generate(),
            pwg.generate()
        ]
        expect(new Set(pws).size).toBe(1)
        expect(pwg.generated).toBe(true)
    })
})
