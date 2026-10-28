# Brand logos

`brands.json` maps app package names (and app labels, as a fallback) to a brand look: a
[Simple Icons](https://simpleicons.org) logo (CC0) plus brand colours. Brands Simple Icons doesn't carry
get a name-only tile in their colours. Apps that ship their own wide TV banner always use that instead.

To add or change a brand, edit `brands.json`, then regenerate the drawables and `Brands.java`:

```
npm pack simple-icons && tar xzf simple-icons-*.tgz
pip install svgelements
python3 gen.py package
```

Brand names and logos belong to their owners; they're used here only to label the apps you have installed.
