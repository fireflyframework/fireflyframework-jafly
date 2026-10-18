# Restore a legacy Java repository

The [legacy records release](https://github.com/fireflyframework/fireflyframework-jafly/releases/tag/legacy%2Fcollaboration-archive%2F20261018T064326Z) contains Git bundles and portable collaboration records for all 63 source repositories. Use this archive when you need an original source tree or a historical pull request context. Develop current Java changes in Jafly.

## Download and verify

Run these commands in a new, empty directory. They download the four published assets and verify the three payloads against the checksum file.

```sh
gh release download 'legacy/collaboration-archive/20261018T064326Z' \
  --repo fireflyframework/fireflyframework-jafly
shasum -a 256 -c SHA256SUMS-legacy-20261018T064326Z.txt
tar -xzf legacy-code-collaboration-20261018T064326Z.tar.gz
```

The checksum file detects incomplete or changed downloads. Keep the downloaded archive and checksums together. The manifest records the original repository names, frozen source heads, trees, and archive anchors.

## Restore an exact source checkout

Each source bundle stores its original refs under archive namespaces. Fetch those refs into a **new bare repository**, then create a local branch at the frozen source head recorded in the manifest. A normal clone alone does not select these custom refs.

This complete example restores the final BOM source tree. The forty zeroes make branch creation fail if `main` already exists.

```sh
git init --bare bom-restored.git
git --git-dir=bom-restored.git fetch --no-tags \
  ./legacy-code-collaboration/fireflyframework-bom/source.bundle 'refs/*:refs/*'
git --git-dir=bom-restored.git update-ref refs/heads/main \
  81541d35df0308d04c6aea6594c38fa579e6b75e 0000000000000000000000000000000000000000
git --git-dir=bom-restored.git symbolic-ref HEAD refs/heads/main
git --git-dir=bom-restored.git fsck --full
git clone --no-hardlinks ./bom-restored.git bom-restored
git -C bom-restored rev-parse 'HEAD^{tree}'
```

The expected tree is `0df4466bf26e7cbc3b3dec6e5ea81dde08965586`. This sequence was tested using the asset downloaded from the public release, without a network fetch or an alternate object store.

For another source repository, use its bundle and `source_head` from `legacy-archive-manifest-20261018T064326Z.json`. Use distinct new bare and checkout directories. The frozen head includes the retirement README; the original implementation commits remain in its history.

## Inspect collaboration history

The tar archive includes portable pull request conversations, reviews, inline comments, release metadata, and source/context bundles. The separate `jafly-legacy-collaboration-anchors-20261018T064326Z.bundle` preserves the 63 additional archive anchors together. To inspect those anchors offline:

```sh
git init --bare collaboration-restored.git
git --git-dir=collaboration-restored.git fetch --no-tags \
  ./jafly-legacy-collaboration-anchors-20261018T064326Z.bundle 'refs/*:refs/*'
git --git-dir=collaboration-restored.git fsck --full
git --git-dir=collaboration-restored.git for-each-ref refs/tags/legacy/
```

The same anchors are protected tags in Jafly under `legacy/<source-repository>/collaboration-archive/20261018T064326Z`. They preserve exact frozen trees and reviewed commits. They do not belong in the active framework branch.

## Archive boundaries

The portable records preserve content; they do not recreate GitHub discussion identities, original repository URLs, or native pull request pages. Workflow run and artifact records were inventoried, but historical Actions logs and artifact payloads were not all downloaded. Historical Maven package binaries and their registry associations are separate from this Git/source archive.

Use a new repository if you later publish a restored source tree. Do not mirror-push these archive refs over Jafly or an existing working repository.
