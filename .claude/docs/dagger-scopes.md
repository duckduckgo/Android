# Dagger scope hierarchy and injection crashes

The hierarchy determines what dependencies each scope can access, where its factory lookup happens,
and which `HasDaggerInjector` performs the injection. It is encoded implicitly in the Anvil-generated
`_SubComponent` files — this diagram is the source of truth:

```
DuckDuckGoApplication [HasDaggerInjector]
└── AppComponent [AppScope]
    │   Factory map contains: ActivityComponent.Factory, ReceiverSubComponent factories,
    │                         ServiceSubComponent factories, VpnScope factories
    │
    ├── ActivityComponent [ActivityScope]  ← subcomponent of AppComponent
    │   │   Factory map contains: FragmentSubComponent factories, ViewSubComponent factories
    │   │   Provided bindings: @ActivityContext Context, AppCompatActivity
    │   │
    │   ├── EachFragment_SubComponent [FragmentScope]  ← subcomponent of ActivityComponent
    │   └── EachView_SubComponent [ViewScope]          ← subcomponent of ActivityComponent
    │
    ├── EachReceiver_SubComponent [ReceiverScope]  ← subcomponent of AppComponent
    └── EachService_SubComponent [ServiceScope]    ← subcomponent of AppComponent
```

`FragmentScope` and `ViewScope` factories are looked up through `DaggerActivity.injectorFactoryMap`.
`ReceiverScope`, `ServiceScope` and `VpnScope` factories are looked up through
`DuckDuckGoApplication.injectorFactoryMap`.

The parent scope follows from the scope passed to `@InjectWith`; Anvil generates the `ParentComponent`
and its `@ContributesTo`. You never set it manually.

## Debugging "could not find dagger component"

- Crash in a Fragment/View injection → check `DaggerActivity.injectorFactoryMap`; the class is missing
  `@InjectWith(FragmentScope::class)` or `@InjectWith(ViewScope::class)`.
- Crash in a Receiver/Service injection → check `DuckDuckGoApplication.injectorFactoryMap`; the class
  is missing `@InjectWith(ReceiverScope::class)` or `@InjectWith(ServiceScope::class)`.

## Member injection into a transitively-typed field

Anvil reporting `Couldn't resolve ClassReference … File is unknown` for an `@Inject` field means the
field's type is only available transitively. Add the module that declares it as a direct dependency.
