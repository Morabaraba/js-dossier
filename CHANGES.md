# v0.9.0

## Bug Fixes

- Properly record documentation for types exported from Closure and Node
   modules (ES6 modules were not affected)
- Stop using ES6 additions to String.prototype; these are not available
   on MSIE

## Changes

- Added new configuration options: `externModules` and `sourceUrlTemplate`
- Added visibility tags for package-private, protected, and protected
   types/properties
- Adjusted page font-weight based on screen dpi
- Adjusted nav tree view so its (hopefully) easier to navigate when there
   is a lot of nesting
- Compiler constants (`@define` and `goog.define(name, value)`) will now
   render using their fully qualified names, exactly as they must be used
   with the compiler's --define flag.
- Dossier will now automatically include extern definitions for Node's core
   modules when the input configuration includes `modules` file list.
- Updates the HTML sanitizer to permit the `cite` attribute on `q` elements.
- Set styles on `kbd` elements
- Started user CHANGES log. For older releases, refer to git commit history