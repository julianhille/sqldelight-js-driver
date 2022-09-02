# sqldelight-js-driver

A kotlin sqldelight js driver using/interop better-sqlite3-multiple-ciphers to allow encrypted databases


## Usage in node / electron after building

After compiling and using it in js you need to make sure to install the better-sqlite3-multiple-cipher binary.
The default installation of this library, as of kotlin-js dependency resolution, does ignore
script execution on installation.

This leads to the issue that `node_modules/better-sqlite3-multiple-ciphers` directory is missing `build`
dir where the native lib is located.

You need to take care of that, before building and distributing a js release.