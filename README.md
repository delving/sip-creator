# Delving SIP-Creator
[![Build Status](https://travis-ci.org/delving/sip-creator.svg)](https://travis-ci.org/delving/sip-creator)
[![codecov.io](https://codecov.io/github/delving/sip-creator/coverage.svg)](https://codecov.io/github/delving/sip-creator)

The Delving SIP-Creator is a Java application with an elaborate graphical user interface for transforming any given input source of XML records into any of a number of defined output record formats.  It is launched in a browser using the Java Web Start technology, and it is currently bundled with the Delving CultureHub server platform.

SIP is short for for "Submission Information Package", a term from [OAIS](https://en.wikipedia.org/wiki/Open_Archival_Information_System) which refers to the packages as they initially arrive online. The SIP-Creator application automatically synchronizes its locally stored data with the online server.

## Transformation

The job of XML transformation is generally performed by XSLT scripts, but these can be hard to build and maintain due to verbosity.  Typically, XML transformation amounts to rearranging the structure such that the content segments of the source are rearranged and copied into the target format. In many cases, however, it is not enough to just copy field contents verbatim, but rather some manipulation must take place on the way.  Fields may need to be combined or split, and field contents may need to be changed on the way, such as turning an identifier into a full image or web page URL.

## Groovy

The core technology of the SIP-Creator is built around on the Groovy programming language, since it is "builder" code in Groovy which actually does the mapping work.  Most of the Groovy code responsible for the mapping, at least its global structure, is generated automatically.

The SIP-Creator user who builds the mapping only has to work on tiny parts of code at any given time, any code that is adjusted by the user is automatically compiled and executed on-the-fly.  This way it is possible for mappers to see the results of their work immediately, and the process of mapping becomes a lot more effective and satisfying.

The key advantage of having a full-fledged dynamic programming language at the mapper's disposal becomes very clear as the trickiness of the corner cases of real-world mapping challenges present themselves.

## Almost Anything Goes

Datasets come from many different places, and from a number of different storage systems, so in order to be able to consume all this variety and make good use of it in the CultureHub, the SIP-Creator is responsible for massaging this data until it is in a normalized standard format that is useful for the online server, and then handling the efficient upload of the data in compressed form.

The only real requirement for what is taken as input to the SIP-Creator is that it be XML encoded with UTF-8.  The structure of that XML will naturally determine how easy or difficult the mapping process is, but generally any form of XML can be consumed.

The source XML can either be imported from an accessible drive/volume on the mapper's own computer or local network, or it can be harvested using the OAI-PMH protocol if there is a server available.

## Statistics

At every step along the way from input to producing the output, statistics are gathered in the form of histograms which are displayed as various bar graphs in a pop-up frame.  Some of these give insight into the quality of the records as a whole, and others let you zoom in on a particular field to view statistics about its contents.

## Validation

To ensure that the output complies to the desired record structure, there is an XML Schema which is separate from the record definition which can independently verify that the output is correct.  In many cases the target format is a known format with an existing schema, which is then simply employed for validation of each record individually.

## Step by Step

The basic work-flow of ingesting metadata into the CultureHub using the SIP-Creator is as follows:

* Create an empty dataset on the CultureHub

* Download the empty dataset to the SIP-Creator's local workspace (contains target record definitions)

* Import metadata into the SIP-Creator either by reading XML locally or fetching it via OAI-PMH

* Have the SIP-Creator analyze the input dataset and show statistics

* Choose the record root and the unique element in the source XML structure

* Convert the metadata to normalized Delving source format

* Have the SIP-Creator analyze the records in source format

* Make mappings from source structure to target record structure by selecting and clicking

* Refine mappings where necessary to do manipulations using Groovy code

* Validate the output of the mapping against the target record structure using the XSD, to ensure that the dataset is ready for using on the CultureHub

* After validation, the dataset can be uploaded to the CultureHub

* Indexing of the dataset for search and navigation is done via the web interface of the CultureHub


## Project Structure

This project consists of two modules, because the part of the code responsible for executing the mapping transformation does its work both on the client (for validation) and on the server (for indexing and rendering).  The remainder of the code is dedicated to presenting a very interactive user interface in which mappers can specify

* __sip-core__
    * code that exists both within the CultureHub and the SIP-Creator
    * responsible for actually executing the mapping transformation
    * on the client: only for validation
    * on the server: for indexing and rendering

* __sip-creator__ -
    * the interactive user interface
    * *live coding*, auto-compilation-execution of Groovy snippets
    * download, import, analysis, mapping, refinement, validation, upload
    * caches data in `~/DelvingSIPCreator` directory

* __schema-repo__ -
    * used to access the schemas stored at http://schemas.delving.eu
    * content of the above site stored at https://github.com/delving/schemas.delving.eu
    * also works with a local clone of the above repository

## Contact

If you want to know more about the SIP-Creator, contact us at __`sip-creator@delving.eu`__

- - -
