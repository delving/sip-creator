
# Changelog


## unreleased

### Added

- Added support for adding ``__cache__`` from the jar when network is not available

### Fixed

- Using single quotes inside facts no longer causes compile errors [[GH-507]](https://github.com/delving/sip-creator/pull/507)
- Disable warnings for Xstream at startup for Sip-App
- Upgrade depandabot alerts

- history of changes: see https://github.com/delving/hub3/compare/v0.2.0...master

## v1.2.0 (2020-03-05)

### Changed
- Each record is now written to a separate output file during processing
- Performance of mapping large inputs to RDF has been increased
- Readability of content in the Mapping Code panel has been improved

### Added
- You can now delete multiple local datasets at the same time after selecting them with CTRL+click
- You can now sort local datasets by name or date
- Returning multiple values from a mapping function will now result in multiple XML elements or XML attributes
- You can now remove duplicated elements from the target panel of the Quick Mapping panel

### Fixed
- Duplicated elements in the target panel of the Quick Mapping panel will now be saved

### Removed
- Delimiter dropdown has been removed from the Code Tweaking panel
- Dictionary tab has been removed from the Code Tweaking panel