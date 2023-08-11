# Unofficial Sodium Culling Patch
__Requires Sodium to Run!__

This is a mod that makes fewer chunks to be loaded in Sodium. The mod mainly implements it by these 2 ways:
- Stealing the codes from [Sodium's dev branch](https://github.com/CaffeineMC/sodium-fabric/tree/dev) to use __a more aggressive distance culling__, which reduces the amount of loaded chunks and improves the fps significantly. The new distance culling system will be included in Sodium 0.5.1, by the way.
- "Reverting" [commit 8a8aad0d](https://github.com/CaffeineMC/sodium-fabric/commit/8a8aad0df3ec36d5246d6a2a6efc1d34a7e092b1). In face of fewer visual errors and higher fps, I choose the latter.

## Compatible with:
- Sodium
- Iris Shader
- Indium
- Sodium Extra
- More Culling
- ...other mods that work with Sodium 0.5


