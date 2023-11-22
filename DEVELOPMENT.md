# Branches

Development take place on the *SWT* branch, which automatically detects your oprating system.
In order to publish your changes, you need to merge them onto the *win64*,*lin64* and *mac64* branches.

# Icons

To generate png from svg run `mvn -Pdownload-svg2png` to download the jar. 
This is a work around for [https://github.com/sterlp/svg2png/issues/20 and only](https://github.com/sterlp/svg2png/issues/20) and only needs to be done once.

Then to transform the svgs to pngs run `mvn -Psvg2png`


