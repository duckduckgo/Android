/**
 * Try to use this a place to store re-used values across the integration tests.
 */
export const constants = {
    pages: {
        'overlay': 'overlay.html',
        'email-autofill': 'email-autofill.html',
        'signup': 'signup.html',
        'login': 'login.html',
        'loginWithPoorForm': 'login-poor-form.html'
    },
    fields: {
        email: {
            personalAddress: `shane-123@duck.com`,
            privateAddress0: '0@duck.com',
            selectors: {
                identity: '[data-ddg-inputtype="identities.emailAddress"]'
            }
        },
        username: {
            selectors: {
                credential: '[data-ddg-inputtype="credentials.username"]'
            }
        },
        password: {
            selectors: {
                credential: '[data-ddg-inputtype="credentials.password"]'
            }
        }
    }
}
