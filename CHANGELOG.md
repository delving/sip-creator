# Changelog

## unreleased

### Added

## v1.2.6 (2022-05-10)

### Added

-  maximum age is 130 and errors can be ignored for builtin functions `calculateAge` and `calculateAgeRange` [[GH520]](https://github.com/delving/sip-creator/pull/520) 
 
- history of changes: see https://github.com/delving/sip-creator/compare/sip-creator-v1.2.5...master

## v1.2.5 (2022-02-10)

### Fixed
 
-  Escape single quotes in facts [d38e7ea](https://github.com/delving/hub3/commit/d38e7ea00484010aa62e609b22adc0757023fb24)

- history of changes: see https://github.com/delving/sip-creator/compare/sip-creator-v1.2.4...v1.2.5

## v1.2.4 (2022-01-20)

### Fixed

- make output files while validation in sip-app optional [[GH-515]](https://github.com/delving/sip-creator/pull/515)

- history of changes: see https://github.com/delving/sip-creator/compare/sip-creator-v1.2.3...v1.2.4

## v1.2.3 (2022-01-05)

### Added

- Add support for NANT record-definition [[GH-514]](https://github.com/delving/sip-creator/pull/514)

### Fixed

- Escape single quotes and restore former getValueNodes behavior [[GH-513]](https://github.com/delving/sip-creator/pull/513)

- history of changes: see https://github.com/delving/sip-creator/compare/sip-creator-v1.2.2...v1.2.3

## v1.2.2 (2021-09-16)

### Added 

- Added display option for rec-def labels  [[GH-512]](https://github.com/delving/sip-creator/pull/512)

### Fixed

- Catch and report any instances of the DiscardRecordException instead of failing the validation [[GH-511]](https://github.com/delving/sip-creator/pull/511)

## v1.2.1 (2021-07-22)

### Added

- Added support for adding ``__cache__`` from the jar when network is not available [[GH-508]](https://github.com/delving/sip-creator/pull/508)
- Support for calculating the age of a person. [[GH-509]](https://github.com/delving/sip-creator/pull/509)

### Fixed

- Using single quotes inside facts no longer causes compile errors [[GH-507]](https://github.com/delving/sip-creator/pull/507)
- Disable warnings for Xstream at startup for Sip-App [[GH-508]](https://github.com/delving/sip-creator/pull/508)
- Upgrade depandabot alerts

- history of changes: see https://github.com/delving/sip-creator/compare/sip-creator-v1.2.0...sip-creator-v1.2.1

## v1.2.0 (2021-03-05)

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
