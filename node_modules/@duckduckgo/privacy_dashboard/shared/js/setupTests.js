const resources = ['shared', 'site', 'connection', 'report', 'permissions']

// eslint-disable-next-line no-undef
jest.mock(
    '../locales/*/*.json',
    () =>
        resources.flatMap((resource) => [
            {
                name: `en/${resource}`,
                module: require(`../locales/en/${resource}.json`)
            },
            {
                name: `cimode/${resource}`,
                module: {}
            }
        ]),
    { virtual: true }
)
