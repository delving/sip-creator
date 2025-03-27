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
String country = '''Austria'''
String baseUrl = '''http://data.jck.nl'''
String schemaVersions = '''edm_5.2.6'''
String provider = '''AB-C Media'''
String dataProviderUri = '''http://id.musip.nl/crm_e39/1736'''
String rights = '''http://www.europeana.eu/rights/unknown/'''
String name = '''jhm-museum'''
String language = '''nl'''
String dataProvider = '''Joods Historisch Museum'''
String type = '''IMAGE'''
String orgId = '''abc-media'''
String spec = '''jhm-museum'''
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
                     if (_record.foto[0]) {
                        if (!_record.foto[0].fotonoweb | !_record.foto[0].fotonoweb_.contains('x')) {
                           "http://media.delving.org/thumbnail/${orgId}/joods-historisch/${_record.foto[0].fotocdnr_}/500".sanitizeURI()
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
                     if (_record.foto[0]) {
                        if (!_record.foto[0].fotonoweb | !_record.foto[0].fotonoweb_.contains('x')) {
                           "http://media.delving.org/thumbnail/${orgId}/joods-historisch/${_record.foto[0].fotocdnr_}/220".sanitizeURI()
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
               _record.NWvervaard * { _NWvervaard ->
                  'dc:creator' { _absent_ = false
                     if (_NWvervaard.NWvervaardnaam.term) {
                        "${_NWvervaard.NWvervaardnaam.term_[0]}"
                     }
                     else {
                        "x"
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.NWdatering * { _NWdatering ->
                  'dc:date' { _absent_ = false
                     "${_NWdatering.NWdateringbeginpr_} ${_NWdatering.NWdateringbegin_}  tot ${_NWdatering.NWdateringeindpr_} ${_NWdatering.NWdateringeind_} ".trim()
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               (_record.geschpubliekja | _record.samenvatting | _record.geschhistorie) * { _M3 ->
                  'dc:description' { _absent_ = false
                     if (_M3['geschpubliekja'] == 'x' && _M3['samenvatting']){
                        "${_M3['samenvatting']} <br/> ${_M3['geschhistorie']}"
                     }
                     else if (_M3['geschpubliekja'] == 'x' && !_M3['samenvatting']) {
                        "${_M3['geschhistorie']}"
                     }
                     else {
                        "${_M3['samenvatting']}"
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               (_record.geschpubliekja | _record.samenvatting | _record.samenvatting) * { _M3 ->
                  'dc:description' { _absent_ = false
                     "${_M3['geschpubliekja']} ${_M3['samenvatting']} ${_M3['samenvatting']}"
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.NWobjectnumber * { _NWobjectnumber ->
                  'dc:identifier' { _absent_ = false
                     "${_NWobjectnumber}"
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.rights * { _rights ->
                  _rights.rightscopyright * { _rightscopyright ->
                     'dc:rights' { _absent_ = false
                        "${_rightscopyright}"
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.publicatievermelding * { _publicatievermelding ->
                  'dc:source' { _absent_ = false
                     "${_publicatievermelding}"
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.keyword * { _keyword ->
                  'dc:subject' { _absent_ = false
                     if (_keyword.term) {
                        "${_keyword.term_}"
                     }
                     else {
                        "x"
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.titel * { _titel ->
                  'dc:title' { _absent_ = false
                     if (_absent_) {
                        "[geen titel]"
                     }
                     else {
                        "${_titel}"
                     }
                  }
               }
            }
            if (_absent_) {
               'dc:title' { 
                  "[geen titel]"
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.trefpersoon * { _trefpersoon ->
                  'dcterms:alternative' { _absent_ = false
                     if (_trefpersoon.NWtrefpersoon.term) {
                        "${_trefpersoon.NWtrefpersoon.term_[0]}"
                     }
                     else {
                        "x"
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.vervaardigingdisplay * { _vervaardigingdisplay ->
                  'dcterms:created' { _absent_ = false
                     "${_vervaardigingdisplay}"
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.NWobjectnumber * { _NWobjectnumber ->
                  'dcterms:isPartOf' { _absent_ = false
                     "${_NWobjectnumber}"
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.trefpersoon * { _trefpersoon ->
                  _trefpersoon.thesIDM * { _thesIDM ->
                     'dcterms:isReferencedBy' { _absent_ = false
                        "${_thesIDM}"
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.keyword * { _keyword ->
                  'dcterms:references' { _absent_ = false
                     if (_keyword.informatie) {
                        "${_keyword.informatie_}"
                     }
                     else {
                        "x"
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.trefpersoon * { _trefpersoon ->
                  'dcterms:replaces' { _absent_ = false
                     if (_trefpersoon.NWtrefpersoon.informatie) {
                        "${_trefpersoon.NWtrefpersoon.informatie_[0]}"
                     }
                     else {
                        "x"
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.trefpersoon * { _trefpersoon ->
                  'dcterms:requires' { _absent_ = false
                     if (_trefpersoon.NWtrefpersoon.ID_Monument) {
                        "${_trefpersoon.NWtrefpersoon.ID_Monument}"
                     }
                     else {
                        "x"
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
         'nave:BrabantCloudResource' { 
            _absent_ = true
            _facts.name * { _name ->
               'nave:collection' { _absent_ = false
                  "${_name}"
               }
            }
            _absent_ = true
            'nave:collectionPart' { _absent_ = false
               'objecten'
            }
            _absent_ = true
            _input.record * { _record ->
               _record.vervaardigingdata * { _vervaardigingdata ->
                  'nave:creatorBirthYear' { _absent_ = false
                     "${_vervaardigingdata.split("=").last()}"
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.vervaardigingdata * { _vervaardigingdata ->
                  'nave:creatorDeathYear' { _absent_ = false
                     "${_vervaardigingdata.split("=").last()}"
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.NWvervaard * { _NWvervaard ->
                  'nave:creatorRole' { _absent_ = false
                     if (_NWvervaard.NWvervaardrol) {
                        "${_NWvervaard.NWvervaardrol_}"
                     }
                     else {
                        "x"
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.NWdatering * { _NWdatering ->
                  'nave:date' { _absent_ = false
                     "${_NWdatering}"
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.dimension * { _dimension ->
                  'nave:dimension' { _absent_ = false
                     "${_dimension.NWdimensiontype_}: ${_dimension.NWdimensionvalue_} ${_dimension.NWdimensionunit_}".trim()
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.NWmaterial * { _NWmaterial ->
                  _NWmaterial.NWMaterial * { _NWMaterial ->
                     'nave:material' { _absent_ = false
                        "${lowercase(_NWMaterial)}"
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.NWobjectnumber * { _NWobjectnumber ->
                  'nave:objectNumber' { _absent_ = false
                     "${_NWobjectnumber}"
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.objectnaam * { _objectnaam ->
                  'nave:objectSoort' { _absent_ = false
                     "${_objectnaam.term_}"
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.NWvervaard * { _NWvervaard ->
                  'nave:place' { _absent_ = false
                     if (_NWvervaard.NWvervaardplaats_) {
                        "${_NWvervaard.NWvervaardplaats_}"
                     }
                     else {
                        "x"
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.NWtrefpersoon * { _NWtrefpersoon ->
                  'nave:subjectDepicted' { _absent_ = false
                     "${_NWtrefpersoon}"
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.vervaardigingmethode * { _vervaardigingmethode ->
                  'nave:technique' { _absent_ = false
                     "${lowercase(_vervaardigingmethode)}"
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.NWtechniek * { _NWtechniek ->
                  _NWtechniek.NWtechniektechniek * { _NWtechniektechniek ->
                     'nave:technique' { _absent_ = false
                        "${lowercase(_NWtechniektechniek)}"
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.foto * { _foto ->
                  'nave:thumbLarge' { _absent_ = false
                     if (!_foto.fotonoweb | !_foto.fotonoweb_.contains('x')) {
                        "http://media.delving.org/thumbnail/${orgId}/joods-historisch/${_foto.fotocdnr_}/500".sanitizeURI()
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.foto * { _foto ->
                  'nave:thumbSmall' { _absent_ = false
                     if (!_foto.fotonoweb | !_foto.fotonoweb_.contains('x')) {
                        "http://media.delving.org/thumbnail/${orgId}/joods-historisch/${_foto.fotocdnr_}/220".sanitizeURI()
                     }
                  }
               }
            }
         }
         'nave:DelvingResource' { 
            _absent_ = true
            _input.record * { _record ->
               _record.foto * { _foto ->
                  'nave:deepZoomUrl' { _absent_ = false
                     if (!_foto.fotonoweb | !_foto.fotonoweb_.contains('x')) {
                        "http://media.delving.org/iip/deepzoom/mnt/tib/tiles/${orgId}/joods-historisch/${_foto.fotocdnr_}.tif.dzi".sanitizeURI()
                     }
                  }
               }
            }
            _absent_ = true
            _input.record * { _record ->
               _record.webkort * { _webkort ->
                  'nave:fullText' { _absent_ = false
                     "${_webkort}"
                  }
               }
            }
         }
      }
   }
   outputNode
}
// ----------------------------------
