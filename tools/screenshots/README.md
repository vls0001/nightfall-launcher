# Screenshot check

Runs the real launcher code in [Robolectric](https://robolectric.org) with fake apps and saves
PNGs of the home screen, All apps, Settings and a pop-up card to `out/`.

```
../../build.sh   # builds resources the test needs
./run.sh
```

`src/test/java/androidx/test/...` holds small stand-ins for the `androidx.test` classes Robolectric
calls, so the check works without Google's Maven repository.
