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
String country = '''Netherlands'''
String baseUrl = '''http://data.brabantcloud.nl'''
String schemaVersions = '''edm_5.2.6'''
String provider = '''Erfgoed Brabant'''
String rights = '''http://creativecommons.org/publicdomain/mark/1.0/'''
String name = '''BHIC'''
String language = '''nl'''
String dataProvider = '''BHIC'''
String type = '''IMAGE'''
String spec = '''bhic'''
String orgId = '''brabantcloud'''
String _uniqueIdentifier = 'UNIQUE_IDENTIFIER'
// Functions from Mapping:
def isShownAtTwo = { it ->
   image = it.toString().replaceAll('bhic:dc3fc860-950b-11e1-bca0-3860770ffe72:','')
   "http://www.bhic.nl/foto/${image}"
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
                  _metadata.oaidcdc * { _oaidcdc ->
                     _oaidcdc.dcidentifier * { _dcidentifier ->
                        'edm:dataProvider' { _absent_ = false
                           if (_dcidentifier.contains("PNB")) "MIP" else "BHIC"
                        }
                     }
                  }
               }
            }
            'edm:isShownAt' (
               'rdf:resource' : {
                  _input.record * { _record ->
                     _record.header * { _header ->
                        if (_header.identifier[0]) {
                           "${isShownAtTwo(_header.identifier[0])}".sanitizeURI()
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
                     _record.about * { _about ->
                        _about.mmmmemorix * { _mmmmemorix ->
                           _mmmmemorix.image * { _image ->
                              "${_image.replaceAll('250x250','640x480')}"
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
                     _record.about * { _about ->
                        _about.mmmmemorix * { _mmmmemorix ->
                           _mmmmemorix.image * { _image ->
                              "${_image}"
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
            'edm:rights' (
               'rdf:resource' : {
                  _facts.rights * { _rights ->
                     "${_rights}"
                  }
               }
            ) { 
               // no node mappings
            }
         }
         'edm:ProvidedCHO' (
            'rdf:about' : {
               
                            "${baseUrl}/resource/document/${spec}/${_uniqueIdentifier.sanitizeURI()}"
            }
         ) { 
            _absent_ = true
            _input.record ** { _record ->
               _record.metadata ** { _metadata ->
                  _metadata.oaidcdc ** { _oaidcdc ->
                     _oaidcdc.dccoverage ** { _dccoverage ->
                        'dc:coverage' { _absent_ = false
                           "${_dccoverage}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.oaidcdc * { _oaidcdc ->
                     _oaidcdc.dcdccreator * { _dcdccreator ->
                        'dc:creator' { _absent_ = false
                           "${reverseNames(_dcdccreator)}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.oaidcdc * { _oaidcdc ->
                     _oaidcdc.dcdate * { _dcdate ->
                        'dc:date' { _absent_ = false
                           items = _dcdate.split("-")
                           if (items.size() == 3) {"${items[2]}-${items[1]}-${items[0]}".replaceAll("00-","")}
                           else if (items.size() == 2) {"${items[2]}, ${items[0]}".replaceAll("00-","")}
                           else if (items.size() == 1) {"${items[0]}".replaceAll("00-","")}
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.oaidcdc * { _oaidcdc ->
                     _oaidcdc.dcdescription * { _dcdescription ->
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
                  _metadata.oaidcdc * { _oaidcdc ->
                     _oaidcdc.dcidentifier * { _dcidentifier ->
                        'dc:identifier' { _absent_ = false
                           "${_dcidentifier}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record ** { _record ->
               _record.metadata ** { _metadata ->
                  _metadata.oaidcdc ** { _oaidcdc ->
                     _oaidcdc.dcdescription ** { _dcdescription ->
                        'dc:title' { _absent_ = false
                           if (_absent_) {
                              "geen titel"
                           }
                           else {
                              id = _dcdescription.toString()
                              if (id.length() > 100) "${_dcdescription.replaceAll("\\. *", "")}" else id
                           }
                        }
                     }
                  }
               }
            }
            if (_absent_) {
               'dc:title' { 
                  "geen titel"
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.oaidcdc * { _oaidcdc ->
                     _oaidcdc.dcformat * { _dcformat ->
                        'dc:type' { _absent_ = false
                           "${lowercase(_dcformat)}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _facts.type * { _type ->
               'edm:type' { _absent_ = false
                  "${_type}"
               }
            }
         }
         'edm:WebResource' (
            'rdf:about' : {
               _input.record * { _record ->
                  _record.about * { _about ->
                     _about.memorix * { _memorix ->
                        _memorix.mrximage * { _mrximage ->
                           "${_mrximage}".sanitizeURI()
                        }
                     }
                  }
               }
            }
         ) { 
         }
         'nave:BrabantCloudResource' { 
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.oaidcdc * { _oaidcdc ->
                     _oaidcdc.dcidentifier * { _dcidentifier ->
                        'nave:collection' { _absent_ = false
                           if (_dcidentifier.contains("PNB")) "MIP" else "BHIC"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.oaidcdc * { _oaidcdc ->
                     _oaidcdc.dcdate * { _dcdate ->
                        'nave:date' { _absent_ = false
                           items = _dcdate.split("-")
                           if (items.size() == 3) {"${items[2]}-${items[1]}-${items[0]}".replaceAll("00-","")}
                           else if (items.size() == 2) {"${items[2]}, ${items[0]}".replaceAll("00-","")}
                           else if (items.size() == 1) {"${items[0]}".replaceAll("00-","")}
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.oaidcdc * { _oaidcdc ->
                     _oaidcdc.dcidentifier * { _dcidentifier ->
                        'nave:objectNumber' { _absent_ = false
                           "${_dcidentifier}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.oaidcdc * { _oaidcdc ->
                     _oaidcdc.dcformat * { _dcformat ->
                        'nave:objectSoort' { _absent_ = false
                           "${lowercase(_dcformat)}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.metadata * { _metadata ->
                  _metadata.oaidcdc * { _oaidcdc ->
                     _oaidcdc.dccoverage * ', ' * { _dccoverage ->
                        'nave:place' { _absent_ = false
                           items = _dccoverage.split(",")
                           if (items.size() == 2) {"${items[1]}, ${items[0]}".replaceAll("^ ", "")}
                           else if (items.size() == 3) {"${items[1]}${items[2]}, ${items[0]}".replaceAll("^ ", "")}
                           else if (items.size() == 1) {"${items[0]}".replaceAll("^ ", "")}
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.about * { _about ->
                  _about.mmmmemorix * { _mmmmemorix ->
                     _mmmmemorix.image * { _image ->
                        'nave:thumbLarge' { _absent_ = false
                           "${_image.replaceAll('250x250','640x480')}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.about * { _about ->
                  _about.mmmmemorix * { _mmmmemorix ->
                     _mmmmemorix.image * { _image ->
                        'nave:thumbSmall' { _absent_ = false
                           "${_image}"
                        }
                     }
                  }
               }
            }
         }
         'nave:DelvingResource' { 
            _absent_ = true
            _input.record * { _record ->
               _record.about * { _about ->
                  _about.mmmmemorix * { _mmmmemorix ->
                     _mmmmemorix.image * { _image ->
                        'nave:thumbnail' { _absent_ = false
                           "${_image}"
                        }
                     }
                  }
               }
            }
            _absent_ = true
            'nave:featured' { _absent_ = false
               'false'
            }
            _absent_ = true
            'nave:allowDeepZoom' { _absent_ = false
               'true'
            }
            _absent_ = true
            'nave:allowLinkedOpenData' { _absent_ = false
               'true'
            }
            _absent_ = true
            'nave:allowSourceDownload' { _absent_ = false
               'false'
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
