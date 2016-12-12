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
String schemaVersions = '''icn_1.0.4, ese_3.4.0, lido_1.0.2'''
String baseUrl = '''http://devorg.localhost'''
String provider = '''Rijksdienst voor het Cultureel Erfgoed'''
String rights = '''http://creativecommons.org/licenses/by-nc/3.0/nl/'''
String name = '''Collectie Schraven'''
String language = '''nl'''
String dataProvider = '''Collectie Schraven'''
String type = '''IMAGE'''
String spec = '''coll-schraven'''
String orgId = '''devorg'''
String _uniqueIdentifier = 'UNIQUE_IDENTIFIER'
// Functions from Mapping:
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
            _facts.dataProvider * { _dataProvider ->
               'edm:dataProvider' { _absent_ = false
                  "${_dataProvider}"
               }
            }
            'edm:isShownBy' (
               'rdf:resource' : {
                  _input.record * { _record ->
                     if (_record.pictureurl[0]) {
                        "${_record.pictureurl[0]}".sanitizeURI()
                     }
                  }
               }
            ) { 
               // no node mappings
            }
            'edm:object' (
               'rdf:resource' : {
                  _input.record * { _record ->
                     if (_record.pictureurl[0]) {
                        "${_record.pictureurl[0]}".sanitizeURI()
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
            _facts.rights * { _rights ->
               'edm:rights' { _absent_ = false
                  "${_rights}"
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
               (_record.voornaam1 | _record.achternaam) * { _M3 ->
                  'dc:creator' { _absent_ = false
                     " ${_M3['voornaam1']} ${_M3['achternaam']}"
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.jaarvanvervaardiging * { _jaarvanvervaardiging ->
                  'dc:date' { _absent_ = false
                     "${_jaarvanvervaardiging}"
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.in * { _in ->
                  'dc:identifier' { _absent_ = false
                     "${_in}"
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.titel * { _titel ->
                  'dc:title' { _absent_ = false
                     "${_titel}"
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
                  _record.pictureurl * { _pictureurl ->
                     "${_pictureurl}".sanitizeURI()
                  }
               }
            }
         ) { 
         }
         'nave:DcnResource' { 
            _absent_ = true
            _input.record * { _record ->
               _record.geb * { _geb ->
                  'nave:creatorYearOfBirth' { _absent_ = false
                     "${_geb}"
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.techniek * { _techniek ->
                  'nave:technique' { _absent_ = false
                     "${_techniek}"
                  }
               }
            }
            _absent_ = true
            'nave:province' { _absent_ = false
               'Noord-Holland'
            }
         }
      }
   }
   outputNode
}
