/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

// SIP-Creator Generated Mapping Code
// ----------------------------------
// Discarding:
import eu.delving.groovy.DiscardRecordException
import eu.delving.metadata.OptList
def discard = { reason -> throw new DiscardRecordException(reason.toString()) }
def discardIf = { thing, reason ->  if (thing) throw new DiscardRecordException(reason.toString()) }
def discardIfNot = { thing, reason ->  if (!thing) throw new DiscardRecordException(reason.toString()) }
Object _facts = WORLD._facts
Object _optLookup = WORLD._optLookup
String country = '''Norway'''
String baseUrl = '''http://data.norvegiana.no'''
String schemaVersions = '''edm_5.2.6'''
String provider = ''''''
String rights = ''''''
String name = ''''''
String language = ''''''
String dataProvider = ''''''
String type = '''IMAGE'''
String orgId = '''kulturnett'''
String spec = '''dimu'''
String _uniqueIdentifier = 'UNIQUE_IDENTIFIER'
// Functions from Mapping:
def convertToIdentifier = { it ->
   def uriBytes = it.toString().getBytes("UTF-8");
   def digest = java.security.MessageDigest.getInstance("SHA-1")
   def hash = new StringBuilder()
   for (Byte b in digest.digest(uriBytes)) {
      hash.append('0123456789ABCDEF'[(b & 0xF0) >> 4])
      hash.append('0123456789ABCDEF'[b & 0x0F])
   }
   "$spec/$hash".toString()
}
// Functions from Record Definition:
def convertToUTM = { it ->
   def utmOut = true
   String string = it.toString()
   def sridMatcher = (~/\s*SRID=(\d+);POINT\((\d+)[, ](\d+)\)/).matcher(string)
   def utm33Matcher = (~/(\d+) V (\d+\.\d+|\d+) *(\d+\.\d+|\d+)/).matcher(string)
   def commaMatcher = (~/(\d+\.\d+|\d+), *(\d+\.\d+|\d+)/).matcher(string)
   def spaceMatcher = (~/(\d+\.\d+|\d+) (\d+\.\d+|\d+)/).matcher(string)
   if (sridMatcher.matches()) {
      def id = sridMatcher[0][1].toInteger()
      def zone = id % 100
      def east = sridMatcher[0][2].toDouble()
      def north = sridMatcher[0][3].toDouble()
      if (utmOut) {
         "${it}"
      }
      else {
         uk.me.jstott.jcoord.LatLng latlng = new uk.me.jstott.jcoord.UTMRef(east, north, 'V' as
                    char, zone).toLatLng()
                
         "${latlng.lat},${latlng.lng}"
      }
   }
   else if (utm33Matcher.matches()) {
      def zone = utm33Matcher[0][1].toInteger()
      def east = utm33Matcher[0][2].toDouble()
      def north = utm33Matcher[0][3].toDouble()
      if (utmOut) {
         "SRID=326${zone};POINT(${east},${north})"
      }
      else {
         uk.me.jstott.jcoord.LatLng latlng = new uk.me.jstott.jcoord.UTMRef(east, north, 'V' as
                    char, zone).toLatLng()
                
         "${latlng.lat},${latlng.lng}"
      }
   }
   else if (spaceMatcher.matches()) {
      def east = spaceMatcher[0][1].toDouble()
      def north = spaceMatcher[0][2].toDouble()
      if (utmOut) {
         "SRID=32633;POINT(${east},${north})"
      }
      else {
         uk.me.jstott.jcoord.LatLng latlng = new uk.me.jstott.jcoord.UTMRef(east, north, 'V' as
                    char, 33).toLatLng()
                
         "${latlng.lat},${latlng.lng}"
      }
   }
   else if (commaMatcher.matches()) {
      def latitude = commaMatcher[0][1].toDouble()
      def longitude = commaMatcher[0][2].toDouble()
      if (utmOut) {
         uk.me.jstott.jcoord.UTMRef utmValue = new
                    uk.me.jstott.jcoord.LatLng(latitude,longitude).toUTMRef()
                
         "SRID=326${utmValue.lngZone};POINT(${utmValue.easting},${utmValue.northing})"
      }
      else {
         "${latitude},${longitude}"
      }
   }
   else {
      ''
   }
}
def convertToLATLONG = { it ->
   def utmOut = false
   String string = it.toString()
   def sridMatcher = (~/\s*SRID=(\d+);POINT\((\d+)[, ](\d+)\)/).matcher(string)
   def utm33Matcher = (~/(\d+) V (\d+\.\d+|\d+) *(\d+\.\d+|\d+)/).matcher(string)
   def commaMatcher = (~/(\d+\.\d+|\d+), *(\d+\.\d+|\d+)/).matcher(string)
   def spaceMatcher = (~/(\d+\.\d+|\d+) (\d+\.\d+|\d+)/).matcher(string)
   if (sridMatcher.matches()) {
      def id = sridMatcher[0][1].toInteger()
      def zone = id % 100
      def east = sridMatcher[0][2].toDouble()
      def north = sridMatcher[0][3].toDouble()
      if (utmOut) {
         "${it}"
      }
      else {
         uk.me.jstott.jcoord.LatLng latlng = new uk.me.jstott.jcoord.UTMRef(east, north, 'V' as
                    char, zone).toLatLng()
                
         "${latlng.lat},${latlng.lng}"
      }
   }
   else if (utm33Matcher.matches()) {
      def zone = utm33Matcher[0][1].toInteger()
      def east = utm33Matcher[0][2].toDouble()
      def north = utm33Matcher[0][3].toDouble()
      if (utmOut) {
         "SRID=326${zone};POINT(${east},${north})"
      }
      else {
         uk.me.jstott.jcoord.LatLng latlng = new uk.me.jstott.jcoord.UTMRef(east, north, 'V' as
                    char, zone).toLatLng()
                
         "${latlng.lat},${latlng.lng}"
      }
   }
   else if (spaceMatcher.matches()) {
      def east = spaceMatcher[0][1].toDouble()
      def north = spaceMatcher[0][2].toDouble()
      if (utmOut) {
         "SRID=32633;POINT(${east},${north})"
      }
      else {
         uk.me.jstott.jcoord.LatLng latlng = new uk.me.jstott.jcoord.UTMRef(east, north, 'V' as
                    char, 33).toLatLng()
                
         "${latlng.lat},${latlng.lng}"
      }
   }
   else if (commaMatcher.matches()) {
      def latitude = commaMatcher[0][1].toDouble()
      def longitude = commaMatcher[0][2].toDouble()
      if (utmOut) {
         uk.me.jstott.jcoord.UTMRef utmValue = new
                    uk.me.jstott.jcoord.LatLng(latitude,longitude).toUTMRef()
                
         "SRID=326${utmValue.lngZone};POINT(${utmValue.easting},${utmValue.northing})"
      }
      else {
         "${latitude},${longitude}"
      }
   }
   else {
      ''
   }
}
def cleanAdlibImageReference = { it ->
   it.replaceAll('; ', '_').replaceAll('JPG', 'jpg').replaceAll(".*?[\\\\|//]",
                    "").replaceAll(" ", "%20").replaceAll("\\[", "%5B").replaceAll("]",
                    "%5D")
                
}
def createOreAggregationUri = { it ->
   "${baseUrl}/resource/aggregation/${spec}/${_uniqueIdentifier.sanitizeURI()}"
}
def createEDMAgentUri = { it ->
   StringBuilder out = new StringBuilder()
   for (char c : it.toString().chars) {
      switch (c) {
         case ' ':
         out.append('%20')
         break;
         case '[':
         out.append('%5B')
         break;
         case ']':
         out.append('%5D')
         break;
         case '\\':
         out.append('%5C')
         break;
         default:
         out.append(c);
      }
   }
   identifier = out.toString()
   "${baseUrl}/resource/agent/${spec}/${identifier}"
}
def createEDMPlaceUri = { it ->
   StringBuilder out = new StringBuilder()
   for (char c : it.toString().chars) {
      switch (c) {
         case ' ':
         out.append('%20')
         break;
         case '[':
         out.append('%5B')
         break;
         case ']':
         out.append('%5D')
         break;
         case '\\':
         out.append('%5C')
         break;
         default:
         out.append(c);
      }
   }
   identifier = out.toString()
   "${baseUrl}/resource/place/${spec}/${identifier}"
}
def deepZoomUrl = { it ->
   image = it.toString().replaceAll('^.*[\\/|\\\\]','').replaceAll('(?i)\\.jpg|\\.jpeg|\\.tif|\\.tiff|\\.png|\\.gif','.tif.dzi')
   
                    "http://media.delving.org/iip/deepzoom/mnt/tib/tiles/${orgId}/${spec}/${image}"
}
def isShownAt = { it ->
   "http://www.thuisinbrabant.nl/${spec}/${it}"
}
def largeThumbnail = { it ->
   "http://media.delving.org/thumbnail/brabantcloud/${spec}/${it.replaceAll('^.*[\\/|\\\\]','').replaceAll('(?i)\\.jpg|\\.jpeg|\\.tif|\\.tiff|\\.png|\\.gif', '')}/500"
}
def lowercase = { it ->
   "${it.toString().toLowerCase()}"
}
def reverseNames = { it ->
   parts = it.toString().split(",")
   if (parts.length > 1) {
      "${parts[1].trim()} ${parts[0]}"
   }
   else {
      "${it}"
   }
}
def smallThumbnail = { it ->
   "http://media.delving.org/thumbnail/${orgId}/${spec}/${it.replaceAll('^.*[\\/|\\\\]','').replaceAll('(?i)\\.jpg|\\.jpeg|\\.tif|\\.tiff|\\.png|\\.gif', '')}/180"
}
def toLocalId = { it ->
   "${spec}/${it}"
}
def createImageRedirect = { it ->
   resource = java.net.URLEncoder.encode(it)
   "${baseUrl}/resolve/${resource}"
}
// Dictionaries:
// DSL Category wraps Builder call:
boolean _absent_ = true
org.w3c.dom.Node outputNode
use (MappingCategory) {
   WORLD.input * { _input ->
      _uniqueIdentifier = _input['@id'][0].toString()
      _absent_ = true
      outputNode = WORLD.output.'edm:RDF' { 
         'ore:Aggregation' (
            'rdf:about' : {
               
                            "${baseUrl}/resource/aggregation/${spec}/${_uniqueIdentifier.sanitizeURI()}"
            }
         ) { 
            'edm:aggregatedCHO' (
               'rdf:resource' : {
                  
                                "${baseUrl}/resource/document/${spec}/${_uniqueIdentifier.sanitizeURI()}"
               }
            ) { 
               // no node mappings
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.europeanadataProvider * { _europeanadataProvider ->
                        'edm:dataProvider' { _absent_ = false
                           "${_europeanadataProvider}"
                        }
                     }
                  }
               }
            }
            'edm:isShownAt' (
               'rdf:resource' : {
                  _input.record * { _record ->
                     _record.metadata * { _metadata ->
                        _metadata.abmrecord * { _abmrecord ->
                           "${_abmrecord.europeanaisShownAt[0]}".sanitizeURI()
                        }
                     }
                  }
               }
            ) { 
               // no node mappings
            }
            'edm:isShownBy' (
               'rdf:resource' : {
                  _input.record * { _record ->
                     _record.metadata * { _metadata ->
                        _metadata.abmrecord * { _abmrecord ->
                           _abmrecord.europeanaisShownBy * { _europeanaisShownBy ->
                              "${_europeanaisShownBy}"
                           }
                        }
                     }
                  }
               }
            ) { 
               // no node mappings
            }
            'edm:object' (
               'rdf:resource' : {
                  _input.record * { _record ->
                     _record.metadata * { _metadata ->
                        _metadata.abmrecord * { _abmrecord ->
                           _abmrecord.europeanaobject * { _europeanaobject ->
                              "${_europeanaobject}"
                           }
                        }
                     }
                  }
               }
            ) { 
               // no node mappings
            }
            _absent_ = true
            _facts.provider * { _provider ->
               'edm:provider' { _absent_ = false
                  "${_provider}"
               }
            }
            _absent_ = true
            _input.record ** { _record ->
               _record.metadata ** { _metadata ->
                  _metadata.abmrecord ** { _abmrecord ->
                     _abmrecord.europeanarights ** { _europeanarights ->
                        'edm:rights' { _absent_ = false
                           if (_absent_) {
                              if ("${_uniqueIdentifier}" =~ /^NTRM\//) {
                                 "http://creativecommons.org/licenses/by-nc-nd/3.0/"
                              }
                              else {
                                 "${rights}"
                              }
                           }
                           else {
                              "${_europeanarights.sanitizeURI()}"
                           }
                        }
                     }
                  }
               }
            }
            if (_absent_) {
               'edm:rights' { 
                  if ("${_uniqueIdentifier}" =~ /^NTRM\//) {
                     "http://creativecommons.org/licenses/by-nc-nd/3.0/"
                  }
                  else {
                     "${rights}"
                  }
               }
            }
         }
         'edm:ProvidedCHO' (
            'rdf:about' : {
               
                            "${baseUrl}/resource/document/${spec}/${_uniqueIdentifier.sanitizeURI()}"
            }
         ) { 
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.dccreator * { _dccreator ->
                        'dc:creator' { _absent_ = false
                           "${_dccreator}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.dcdate * { _dcdate ->
                        'dc:date' { _absent_ = false
                           if (_dcdate) {
                              parts = _dcdate.split(';')
                              if (parts.length > 2) {
                                 first = parts[0].replaceAll('start=', '').replaceAll('-01-01', '')
                                 second = parts[1].replaceAll('end=', '').replaceAll('-01-01', '')
                              }
                              else {
                                 first = parts[0].replaceAll('start=', '').replaceAll('-01-01', '')
                                 second = first
                              }
                              if (first == second) {
                                 "${first}"
                              }
                              else {
                                 "${first} - ${second}"
                              }
                           }
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.dcdescription * { _dcdescription ->
                        'dc:description' { _absent_ = false
                           "${_dcdescription}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.dcidentifier * { _dcidentifier ->
                        'dc:identifier' { _absent_ = false
                           "${_dcidentifier}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            'dc:language' { _absent_ = false
               'no'
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.dcrights * { _dcrights ->
                        'dc:rights' { _absent_ = false
                           "${_dcrights}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.dcsource * { _dcsource ->
                        'dc:source' { _absent_ = false
                           "${_dcsource}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.dcsubject * { _dcsubject ->
                        'dc:subject' { _absent_ = false
                           "${_dcsubject}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     'dc:title' { _absent_ = false
                        if (_abmrecord.dctitle) {
                           _abmrecord.dctitle
                        }
                        else {
                           "${_abmrecord.dcdescription[0].toString().replaceFirst(/[,.].*$/, "")}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.dctype * { _dctype ->
                        'dc:type' { _absent_ = false
                           "${_dctype}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.dctermsextent * { _dctermsextent ->
                        'dcterms:extent' { _absent_ = false
                           "${_dctermsextent}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.dctermsisPartOf * { _dctermsisPartOf ->
                        'dcterms:isPartOf' { _absent_ = false
                           if (_dctermsisPartOf && _dctermsisPartOf.toString().startsWith("NMK")) {
                              discard("NMK should not be included")
                           }
                           else if (_dctermsisPartOf) {
                              "${_dctermsisPartOf}"
                           }
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.dctermsmedium * { _dctermsmedium ->
                        'dcterms:medium' { _absent_ = false
                           "${_dctermsmedium}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.dctermsprovenance * { _dctermsprovenance ->
                        'dcterms:provenance' { _absent_ = false
                           "${_dctermsprovenance}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.dctermsreferences * { _dctermsreferences ->
                        'dcterms:references' { _absent_ = false
                           if (_dctermsreferences.a_ && ("${_dctermsreferences}" == "")) {
                              def link = _dctermsreferences.a_
                              "${link['@href'][0].sanitizeURI()} ${link}"
                           }
                           else {
                              "${_dctermsreferences}"
                           }
                        }
                     }
                  }
               }
            }
            'dcterms:spatial' (
               'rdf:resource' : {
                  _input.record * { _record ->
                     _record.metadata * { _metadata ->
                        _metadata.abmrecord * { _abmrecord ->
                           latLon = "${_abmrecord.abmlat_},${_abmrecord.abmlong_}"
                           if (latLon != ",") {
                              "${createEDMPlaceUri(latLon)}"
                           }
                        }
                     }
                  }
               }
            ) { 
               // no node mappings
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.dctermstemporal * { _dctermstemporal ->
                        'dcterms:temporal' { _absent_ = false
                           "${_dctermstemporal}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.europeanatype * { _europeanatype ->
                        'edm:type' { _absent_ = false
                           if (_absent_) {
                              "${type}"
                           }
                           else {
                              "${_europeanatype}"
                           }
                        }
                     }
                  }
               }
            }
            if (_absent_) {
               'edm:type' { 
                  "${type}"
               }
            }
         }
         _absent_ = true
         _input.record * { _record ->
            _record.metadata * { _metadata ->
               _metadata.abmrecord * { _abmrecord ->
                  _abmrecord.abmimage * { _abmimage ->
                     'edm:WebResource' (
                        'rdf:about' : {
                           _abmimage.abmimageUri * { _abmimageUri ->
                              "${_abmimageUri}"
                           }
                        }
                     ) { 
                        _absent_ = true
                        'dc:creator' { _absent_ = false
                           _abmimage.dccreator
                        }
                        _absent_ = true
                        'nave:allowDeepZoom' { _absent_ = false
                           'true'
                        }
                        _absent_ = true
                        'nave:allowSourceDownload' { _absent_ = false
                           'true'
                        }
                        _absent_ = true
                        'nave:allowPublicWebView' { _absent_ = false
                           'true'
                        }
                     }
                  }
               }
            }
         }
         _absent_ = true
         _input.record * { _record ->
            _record.metadata * { _metadata ->
               _metadata.abmrecord * { _abmrecord ->
                  _abmrecord.abmmedia * { _abmmedia ->
                     'edm:WebResource' (
                        'rdf:about' : {
                           if (_abmmedia.abmsoundUri) {
                              _abmmedia.abmsoundUri
                           }
                           else if (_abmmedia.abmvideoUri_) {
                              _abmmedia.abmvideoUri_
                           }
                        }
                     ) { 
                        _absent_ = true
                        _abmmedia.description * { _description ->
                           'dc:description' { _absent_ = false
                              "${_description}"
                           }
                        }
                        _absent_ = true
                        'nave:allowDeepZoom' { _absent_ = false
                           'true'
                        }
                        _absent_ = true
                        'nave:allowSourceDownload' { _absent_ = false
                           'true'
                        }
                        _absent_ = true
                        'nave:allowPublicWebView' { _absent_ = false
                           'true'
                        }
                     }
                  }
               }
            }
         }
         'edm:Place' (
            'rdf:about' : {
               _input.record * { _record ->
                  _record.metadata * { _metadata ->
                     _metadata.abmrecord * { _abmrecord ->
                        latLon = "${_abmrecord.abmlat_},${_abmrecord.abmlong_}"
                        if (latLon != ",") {
                           "${createEDMPlaceUri(latLon)}"
                        }
                     }
                  }
               }
            }
         ) { 
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.abmlat * { _abmlat ->
                        'wgs84_pos:lat' { _absent_ = false
                           "${_abmlat}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.abmlong * { _abmlong ->
                        'wgs84_pos:long' { _absent_ = false
                           "${_abmlong}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.dctermsspatial * { _dctermsspatial ->
                        'skos:prefLabel' { _absent_ = false
                           "${_dctermsspatial}"
                        }
                     }
                  }
               }
            }
         }
         'nave:NorvegianaResource' { 
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.abmmunicipality * { _abmmunicipality ->
                        'nave:municipality' { _absent_ = false
                           "${_abmmunicipality}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.abmmunicipalityNr * { _abmmunicipalityNr ->
                        'nave:municipalityNr' { _absent_ = false
                           "${_abmmunicipalityNr}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.abmaboutPerson * { _abmaboutPerson ->
                        'nave:aboutPerson' { _absent_ = false
                           "${_abmaboutPerson}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     'nave:county' { _absent_ = false
                        if (_abmrecord.abmcounty) {
                           "${_abmrecord.abmcounty_}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.abmcountyNr * { _abmcountyNr ->
                        'nave:countyNr' { _absent_ = false
                           "${_abmcountyNr}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.abmcountry * { _abmcountry ->
                        'nave:country' { _absent_ = false
                           "${_abmcountry}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.abmnamedPlace * { _abmnamedPlace ->
                        'nave:namedPlace' { _absent_ = false
                           "${_abmnamedPlace}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            'nave:contentProvider' { _absent_ = false
               'DigitaltMuseum'
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.abmaddress * { _abmaddress ->
                        'nave:address' { _absent_ = false
                           "${_abmaddress}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.abmdigitised * { _abmdigitised ->
                        'nave:digitised' { _absent_ = false
                           "${_abmdigitised}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.abmclassification * { _abmclassification ->
                        'nave:classification' { _absent_ = false
                           "${_abmclassification.replaceAll('\\(.*?\\)','')}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.abmtype * { _abmtype ->
                        'nave:type' { _absent_ = false
                           "${_abmtype}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.abmmedia * { _abmmedia ->
                        _abmmedia.abmvideoUri * { _abmvideoUri ->
                           'nave:videoUri' { _absent_ = false
                              "${_abmvideoUri}"
                           }
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.abmmedia * { _abmmedia ->
                        _abmmedia.abmsoundUri * { _abmsoundUri ->
                           'nave:soundUri' { _absent_ = false
                              "${_abmsoundUri}"
                           }
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.abmrecord * { _abmrecord ->
                     _abmrecord.abmimage * { _abmimage ->
                        _abmimage.abmimageUri * { _abmimageUri ->
                           'nave:imageUri' { _absent_ = false
                              "${_abmimageUri.sanitizeURI()}"
                           }
                        }
                     }
                  }
               }
            }
         }
         'nave:DelvingResource' { 
            _absent_ = true
            'nave:featured' { _absent_ = false
               'false'
            }
            _absent_ = true
            'nave:allowLinkedOpenData' { _absent_ = false
               'true'
            }
            _absent_ = true
            'nave:public' { _absent_ = false
               'true'
            }
         }
      }
   }
   outputNode
}
// ----------------------------------
