// SIP-Creator Generated Mapping Code
// ----------------------------------
// 
// INSTRUCTIONS FOR AI ASSISTANTS (Claude, etc.)
// =============================================
// 
// This is a SIP-Creator mapping file that transforms XML metadata records. When helping users
// with mapping code, please consider the following context and capabilities:
//
// CONTEXT:
// - This code executes within the SIP-Creator's "code tweaking" pane for individual node mappings
// - The code shown here is the COMPLETE generated mapping, but users typically work on small
//   snippets within individual node-mapping elements in the mapping.xml file
// - Each snippet has access to the current input node context and all functions defined here
//
// AVAILABLE VARIABLES IN MAPPING CONTEXT:
// - it: The current input node (GroovyNode object) being processed
// - _input: The root input record (GroovyNode)
// - _uniqueIdentifier: The unique ID for this record
// - _facts: Mapping facts/metadata (provider, baseUrl, etc.)
// - _optLookup: Access to controlled vocabularies/option lists
// - _absent_: Boolean flag for conditional mapping
//
// GROOVYNODE NAVIGATION (key methods):
// - node.get("elementName"): Returns all child elements with that name as a list
// - node.get("elementName_"): Returns FIRST element with non-empty text (note the underscore)
// - node.get("@attributeName"): Returns attribute value
// - node.get("*"): Returns all child nodes
// - node.text(): Returns the text content of the node
// - node.toString(): Converts node to string (same as text())
// - node.getValueNodes("name"): Recursively finds ALL nodes with that name having values
//
// STRING MANIPULATION FUNCTIONS (via MappingCategory):
// - string.sanitize(): Removes newlines and extra spaces
// - string.sanitizeURI(): Encodes for URIs (spaces→%20, []→%5B%5D, \→%5C)
// - string.replaceAll(regex, replacement): Pattern-based replacement
// - string.split(regex): Split by pattern
// - string.matches(regex): Check pattern match
//
// LIST OPERATIONS (special operators):
// - list1 + list2: Concatenate lists
// - list * { closure }: Apply to each element
// - list ** { closure }: Apply to first element only
// - list >> { closure }: Apply once with all elements
// - keys | values: Create map from two lists
//
// COMMON PATTERNS:
//
// 1. Safe navigation with default:
//    it.get("title_").toString() ?: "Unknown Title"
//
// 2. Processing multiple values:
//    it.creator * { creator -> creator.toString().sanitize() }
//
// 3. Building URIs:
//    "${baseUrl}/resource/${it.identifier.sanitizeURI()}"
//
// 4. Conditional mapping:
//    if (it.status != "deleted") { return it.title.toString() }
//
// 5. Complex navigation:
//    it.metadata.record.title.get("@lang")
//
// 6. First non-empty from multiple fields:
//    it.get("title_").toString() ?: it.get("alternativeTitle_").toString() ?: "Untitled"
//
// BEST PRACTICES FOR MAPPING CODE:
// 1. Always check for node existence before accessing
// 2. Use sanitize() for text fields, sanitizeURI() for URI components
// 3. Remember that node access returns lists - use [0] or _ suffix for single values
// 4. Return null to skip mapping a field
// 5. Use meaningful variable names in closures
// 6. Test with edge cases: empty fields, special characters, multiple values
//
// DEBUGGING TIPS:
// - The 'it' variable represents the current input node context
// - Use toString() to safely convert nodes to strings
// - Empty nodes return empty strings, not null
// - Check the input XML structure if paths don't match
//
// UNDERSTANDING GROOVYNODE AND XML STRUCTURE:
// 
// GroovyNode represents XML elements with:
// - Parent/child relationships forming a tree structure
// - Attributes accessible via @attributeName
// - Text content via text() or toString()
// - Child elements accessible by name
//
// Example XML structures being mapped:
//
// LIDO format (hierarchical museum data):
// <input id="001">
//   <lidolido>
//     <lidodescriptiveMetadata>
//       <lidoeventWrap>
//         <lidoeventSet>
//           <lidoevent>
//             <lidoeventActor>
//               <lidoactorInRole>
//                 <lidoactor>
//                   <lidoappellationValue>Van Gogh</lidoappellationValue>
//                 </lidoactor>
//               </lidoactorInRole>
//             </lidoeventActor>
//           </lidoevent>
//         </lidoeventSet>
//       </lidoeventWrap>
//     </lidodescriptiveMetadata>
//   </lidolido>
// </input>
//
// Dublin Core format (flat structure):
// <record>
//   <dc:title>Starry Night</dc:title>
//   <dc:creator>Vincent van Gogh</dc:creator>
//   <dc:subject>Post-Impressionism</dc:subject>
//   <dc:subject>Night scenes</dc:subject>
// </record>
//
// LOOPING AND ITERATION PATTERNS:
//
// The * operator creates loops through node lists:
// _input.lidolido * { _lidolido ->
//     // This closure runs for EACH lidolido element found
//     _lidolido.lidoeventWrap * { _lidoeventWrap ->
//         // Nested loops for deeper navigation
//     }
// }
//
// Loop operators explained:
// - * (spread): Process EACH element in the list
//   node.creator * { creator -> ... }  // loops through all creators
//
// - ** (double spread): Process only FIRST element
//   node.title ** { title -> ... }  // only first title
//
// - >> (flow): Process ENTIRE list at once
//   node.subjects >> { allSubjects -> ... }  // receives full list
//
// The loops handle:
// - Multiple values (multiple subjects, creators, etc.)
// - Missing elements (loop simply doesn't execute)
// - Deep nesting (LIDO's complex hierarchies)
// - Creating multiple outputs from one input
//
// IMPORTANT: When writing snippets for individual mappings, you're typically
// inside one of these loop contexts, so 'it' refers to the current node
// being processed in that specific loop iteration.
//
// ----------------------------------
// Discarding:
import eu.delving.groovy.DiscardRecordException
import eu.delving.metadata.OptList
def discard = { reason -> throw new DiscardRecordException(reason.toString()) }
def discardIf = { thing, reason ->  if (thing) throw new DiscardRecordException(reason.toString()) }
def discardIfNot = { thing, reason ->  if (!thing) throw new DiscardRecordException(reason.toString()) }
Object _facts = WORLD._facts
Object _optLookup = WORLD._optLookup
String baseUrl = '''http://data.collectienederland.nl'''
String schemaVersions = '''edm_5.2.6'''
String dataProviderURL = ''''''
String provider = '''Rijksdienst voor het Cultureel Erfgoed'''
String dataType = ''''''
String rights = '''http://rightsstatements.org/vocab/InC/1.0/'''
String name = '''Van Gogh Museum'''
String language = '''nl'''
String dataProvider = '''Van Gogh Museum'''
String type = ''''''
String orgId = '''dcn'''
String spec = '''van-gogh-museum'''
String _uniqueIdentifier = 'UNIQUE_IDENTIFIER'
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

@groovy.transform.CompileStatic
String calculateAge(String birthDate, String deathDate, boolean automaticDateReordering = false, boolean ignoreErrors = false) { // #def
    def dateFormatter = new java.text.SimpleDateFormat("yyyy-MM-dd")
    if (birthDate == null
        || deathDate == null
        || birthDate.isEmpty()
        || deathDate.isEmpty()
        || birthDate == "null"
        || deathDate == "null") {
        return ""
    }

    def parsedBirthDate
    def parsedDeathDate
    try {
        parsedBirthDate = dateFormatter.parse(birthDate);
    } catch (java.text.ParseException e) {
        if (ignoreErrors) {
            return ""
        }
        throw new IllegalArgumentException("unable to parse birth date", e)
    }
    try {
        parsedDeathDate = dateFormatter.parse(deathDate);
    } catch (java.text.ParseException e) {
        if (ignoreErrors) {
            return ""
        }
        throw new IllegalArgumentException("unable to parse death date", e)
    }

    if (parsedBirthDate.after(parsedDeathDate)) {
        if (!automaticDateReordering) {
            if (ignoreErrors) {
                return ""
            }
            throw new IllegalArgumentException("birth date " + birthDate + " is more recent than death date " + deathDate)
        } else {
            def birth = parsedBirthDate
            def death = parsedDeathDate
            parsedDeathDate = birth
            parsedBirthDate = death
        }
    }
    def ageInMilliseconds = parsedDeathDate.getTime() - parsedBirthDate.getTime();

    Calendar calendar = Calendar.getInstance()
    calendar.setTimeInMillis(ageInMilliseconds)
    def age = calendar.get(Calendar.YEAR) - 1970
    if (age > 130) {
        return ""
    }
    return String.valueOf(age)
}

@groovy.transform.CompileStatic
String calculateAgeRange(String birthDate, String deathDate, boolean automaticDateReordering = false, boolean ignoreErrors = false) { // #def
    def age = calculateAge(birthDate, deathDate, automaticDateReordering, ignoreErrors)
    if(age == "") {
        return ""
    }

    age = Integer.parseInt(age)
    if(age <= 10) {
        return "0 – 10"
    }
    if(age > 100) {
        return "100 – 130"
    }

    def rangeStart = age - (age - 1) % 10
    def rangeEnd = rangeStart + 9
    return rangeStart + " – " + rangeEnd
}
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
         uk.me.jstott.jcoord.LatLng latlng = new uk.me.jstott.jcoord.UTMRef(east, north, 'V' as char, zone).toLatLng()
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
         uk.me.jstott.jcoord.LatLng latlng = new uk.me.jstott.jcoord.UTMRef(east, north, 'V' as char, zone).toLatLng()
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
         uk.me.jstott.jcoord.LatLng latlng = new uk.me.jstott.jcoord.UTMRef(east, north, 'V' as char, 33).toLatLng()
         "${latlng.lat},${latlng.lng}"
      }
   }
   else if (commaMatcher.matches()) {
      def latitude = commaMatcher[0][1].toDouble()
      def longitude = commaMatcher[0][2].toDouble()
      if (utmOut) {
         uk.me.jstott.jcoord.UTMRef utmValue = new uk.me.jstott.jcoord.LatLng(latitude,longitude).toUTMRef()
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
         uk.me.jstott.jcoord.LatLng latlng = new uk.me.jstott.jcoord.UTMRef(east, north, 'V' as char, zone).toLatLng()
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
         uk.me.jstott.jcoord.LatLng latlng = new uk.me.jstott.jcoord.UTMRef(east, north, 'V' as char, zone).toLatLng()
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
         uk.me.jstott.jcoord.LatLng latlng = new uk.me.jstott.jcoord.UTMRef(east, north, 'V' as char, 33).toLatLng()
         "${latlng.lat},${latlng.lng}"
      }
   }
   else if (commaMatcher.matches()) {
      def latitude = commaMatcher[0][1].toDouble()
      def longitude = commaMatcher[0][2].toDouble()
      if (utmOut) {
         uk.me.jstott.jcoord.UTMRef utmValue = new uk.me.jstott.jcoord.LatLng(latitude,longitude).toUTMRef()
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
   it.replaceAll('; ', '_').replaceAll('JPG', 'jpg').replaceAll(".*?[\\\\|//]", "").replaceAll(" ", "%20").replaceAll("\\[", "%5B").replaceAll("]", "%5D")
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
def reverseNamesCorrected = { it ->
   parts = it.toString().split(',')
   if (parts.length > 1) {
      "${parts1.trim()} ${parts0}".replaceAll("(.*)(\\(.*?\\))(.*)", "\$1 \$3 \$2").replaceAll("[\\s]{2,5}", " ")
   }
   else {
      "${it}"
   }
}
def create_geoname_uri = { it ->
   "http://sws.geonames.org/${it}"
}
def remove_whitespace = { it ->
   "${it}".replaceAll("[ ]{2,15}", " ").replaceAll(" \$", "")
}
// Dictionaries:
// DSL Category wraps Builder call:
boolean _absent_ = true
def outputNode
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
            'edm:isShownAt' (
               'rdf:resource' : {
                  _input.lidolido * { _lidolido ->
                     _lidolido.lidodescriptiveMetadata * { _lidodescriptiveMetadata ->
                        _lidodescriptiveMetadata.lidoobjectIdentificationWrap * { _lidoobjectIdentificationWrap ->
                           _lidoobjectIdentificationWrap.lidorepositoryWrap * { _lidorepositoryWrap ->
                              _lidorepositoryWrap.lidorepositorySet * { _lidorepositorySet ->
                                 _lidorepositorySet.lidoworkID * { _lidoworkID ->
                                    "https://www.vangoghmuseum.nl/nl/collectie/${_lidoworkID}"
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            ) {
               // no node mappings
            }
            'edm:isShownBy' (
               'rdf:resource' : {
                  _input.lidolido * { _lidolido ->
                     _lidolido.lidodescriptiveMetadata * { _lidodescriptiveMetadata ->
                        _lidodescriptiveMetadata.lidoobjectIdentificationWrap * { _lidoobjectIdentificationWrap ->
                           _lidoobjectIdentificationWrap.lidorepositoryWrap * { _lidorepositoryWrap ->
                              _lidorepositoryWrap.lidorepositorySet * { _lidorepositorySet ->
                                 _lidorepositorySet.lidoworkID * { _lidoworkID ->
                                    "urn:van-gogh-museum/${_lidoworkID}".replaceAll(".jpg", "").sanitizeURI()
                                 }
                              }
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
                  _input.lidolido * { _lidolido ->
                     _lidolido.lidodescriptiveMetadata * { _lidodescriptiveMetadata ->
                        _lidodescriptiveMetadata.lidoobjectIdentificationWrap * { _lidoobjectIdentificationWrap ->
                           _lidoobjectIdentificationWrap.lidorepositoryWrap * { _lidorepositoryWrap ->
                              _lidorepositoryWrap.lidorepositorySet * { _lidorepositorySet ->
                                 _lidorepositorySet.lidoworkID * { _lidoworkID ->
                                    "urn:van-gogh-museum/${_lidoworkID}".replaceAll(".jpg", "").sanitizeURI()
                                 }
                              }
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
         }
         'edm:ProvidedCHO' (
            'rdf:about' : {
               "${baseUrl}/resource/document/${spec}/${_uniqueIdentifier.sanitizeURI()}"
            }
         ) {
            'dc:creator' (
               'rdf:resource' : {
                  _input.lidolido * { _lidolido ->
                     _lidolido.lidodescriptiveMetadata * { _lidodescriptiveMetadata ->
                        _lidodescriptiveMetadata.lidoeventWrap * { _lidoeventWrap ->
                           _lidoeventWrap.lidoeventSet * { _lidoeventSet ->
                              _lidoeventSet.lidoevent * { _lidoevent ->
                                 _lidoevent.lidoeventActor * { _lidoeventActor ->
                                    _lidoeventActor.lidoactorInRole * { _lidoactorInRole ->
                                       _lidoactorInRole.lidoactor * { _lidoactor ->
                                          if (_lidoactor.lidoactorID) {
                                             _lidoactor.lidoactorID * { _lidoactorID ->
                                                if (!"${_lidoactorID}".contains("ulan")) {
                                                   "${_lidoactorID}"
                                                }
                                             }
                                             }else {
                                             name = _lidoactor.getValueNodes("lidoappellationValue").first()
                                             createEDMAgentUri(name).sanitizeURI()
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            ) {
               // no node mappings
            }
            _absent_ = true
            _input.lidolido * { _lidolido ->
               _lidolido.lidodescriptiveMetadata * { _lidodescriptiveMetadata ->
                  _lidodescriptiveMetadata.lidoeventWrap * { _lidoeventWrap ->
                     _lidoeventWrap.lidoeventSet * { _lidoeventSet ->
                        _lidoeventSet.lidoevent * { _lidoevent ->
                           _lidoevent.lidoeventDate * { _lidoeventDate ->
                              _lidoeventDate.lidodisplayDate * { _lidodisplayDate ->
                                 'dc:date' { _absent_ = false
                                    "${_lidodisplayDate}"
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.lidolido * { _lidolido ->
               _lidolido.lidodescriptiveMetadata * { _lidodescriptiveMetadata ->
                  _lidodescriptiveMetadata.lidoobjectIdentificationWrap * { _lidoobjectIdentificationWrap ->
                     _lidoobjectIdentificationWrap.lidorepositoryWrap * { _lidorepositoryWrap ->
                        _lidorepositoryWrap.lidorepositorySet * { _lidorepositorySet ->
                           _lidorepositorySet.lidoworkID * { _lidoworkID ->
                              'dc:identifier' { _absent_ = false
                                 "${_lidoworkID}"
                              }
                           }
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.lidolido * { _lidolido ->
               _lidolido.lidodescriptiveMetadata * { _lidodescriptiveMetadata ->
                  _lidodescriptiveMetadata.lidoobjectIdentificationWrap * { _lidoobjectIdentificationWrap ->
                     _lidoobjectIdentificationWrap.lidotitleWrap * { _lidotitleWrap ->
                        _lidotitleWrap.lidotitleSet * { _lidotitleSet ->
                           _lidotitleSet.lidoappellationValue * { _lidoappellationValue ->
                              'dc:title' { _absent_ = false
                                 "${_lidoappellationValue}"
                              }
                           }
                        }
                     }
                  }
               }
            }
            'dc:type' (
               'rdf:resource' : {
                  _input.lidolido * { _lidolido ->
                     _lidolido.lidodescriptiveMetadata * { _lidodescriptiveMetadata ->
                        _lidodescriptiveMetadata.lidoobjectClassificationWrap * { _lidoobjectClassificationWrap ->
                           _lidoobjectClassificationWrap.lidoobjectWorkTypeWrap * { _lidoobjectWorkTypeWrap ->
                              _lidoobjectWorkTypeWrap.lidoobjectWorkType * { _lidoobjectWorkType ->
                                 _lidoobjectWorkType.lidoconceptID * { _lidoconceptID ->
                                    "${_lidoconceptID}"
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            ) {
               // no node mappings
            }
            _absent_ = true
            _input.lidolido * { _lidolido ->
               _lidolido.lidodescriptiveMetadata * { _lidodescriptiveMetadata ->
                  _lidodescriptiveMetadata.lidoobjectIdentificationWrap * { _lidoobjectIdentificationWrap ->
                     _lidoobjectIdentificationWrap.lidoobjectMeasurementsWrap * { _lidoobjectMeasurementsWrap ->
                        _lidoobjectMeasurementsWrap.lidoobjectMeasurementsSet * { _lidoobjectMeasurementsSet ->
                           _lidoobjectMeasurementsSet.lidodisplayObjectMeasurements * { _lidodisplayObjectMeasurements ->
                              'dcterms:extent' { _absent_ = false
                                 "${_lidodisplayObjectMeasurements}"
                              }
                           }
                        }
                     }
                  }
               }
            }
            'dcterms:medium' (
               'rdf:resource' : {
                  _input.lidolido * { _lidolido ->
                     _lidolido.lidodescriptiveMetadata * { _lidodescriptiveMetadata ->
                        _lidodescriptiveMetadata.lidoeventWrap * { _lidoeventWrap ->
                           _lidoeventWrap.lidoeventSet * { _lidoeventSet ->
                              _lidoeventSet.lidoevent * { _lidoevent ->
                                 _lidoevent.lidoeventMaterialsTech * { _lidoeventMaterialsTech ->
                                    _lidoeventMaterialsTech.lidotermMaterialsTech * { _lidotermMaterialsTech ->
                                       _lidotermMaterialsTech.lidoconceptID * { _lidoconceptID ->
                                          "${_lidoconceptID}"
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            ) {
               // no node mappings
            }
         }
         'edm:WebResource' (
            'rdf:about' : {
               _input.lidolido * { _lidolido ->
                  _lidolido.lidodescriptiveMetadata * { _lidodescriptiveMetadata ->
                     _lidodescriptiveMetadata.lidoobjectIdentificationWrap * { _lidoobjectIdentificationWrap ->
                        _lidoobjectIdentificationWrap.lidorepositoryWrap * { _lidorepositoryWrap ->
                           _lidorepositoryWrap.lidorepositorySet * { _lidorepositorySet ->
                              _lidorepositorySet.lidoworkID * { _lidoworkID ->
                                 "urn:van-gogh-museum/${_lidoworkID}".replaceAll(".jpg", "").sanitizeURI()
                              }
                           }
                        }
                     }
                  }
               }
            }
         ) {
            _absent_ = true
            _input.lidolido * { _lidolido ->
               _lidolido.lidoadministrativeMetadata * { _lidoadministrativeMetadata ->
                  _lidoadministrativeMetadata.lidoresourceWrap * { _lidoresourceWrap ->
                     _lidoresourceWrap.lidoresourceSet * { _lidoresourceSet ->
                        _lidoresourceSet.lidoresourceRepresentation * { _lidoresourceRepresentation ->
                           _lidoresourceRepresentation.lidolinkResource * { _lidolinkResource ->
                              'nave:thumbSmall' { _absent_ = false
                                 _input.record * { _record ->
                                    "urn:van-gogh-museum/${_lidolinkResource.replaceAll(".jpg", "").sanitizeURI()}"
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.lidolido * { _lidolido ->
               _lidolido.lidoadministrativeMetadata * { _lidoadministrativeMetadata ->
                  _lidoadministrativeMetadata.lidoresourceWrap * { _lidoresourceWrap ->
                     _lidoresourceWrap.lidoresourceSet * { _lidoresourceSet ->
                        _lidoresourceSet.lidoresourceRepresentation * { _lidoresourceRepresentation ->
                           _lidoresourceRepresentation.lidolinkResource * { _lidolinkResource ->
                              'nave:thumbMedium' { _absent_ = false
                                 _input.record * { _record ->
                                    "urn:van-gogh-museum/${_lidolinkResource.replaceAll(".jpg", "").sanitizeURI()}"
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
            _absent_ = true
            _input.lidolido * { _lidolido ->
               _lidolido.lidoadministrativeMetadata * { _lidoadministrativeMetadata ->
                  _lidoadministrativeMetadata.lidoresourceWrap * { _lidoresourceWrap ->
                     _lidoresourceWrap.lidoresourceSet * { _lidoresourceSet ->
                        _lidoresourceSet.lidoresourceRepresentation * { _lidoresourceRepresentation ->
                           _lidoresourceRepresentation.lidolinkResource * { _lidolinkResource ->
                              'nave:thumbLarge' { _absent_ = false
                                 _input.record * { _record ->
                                    "urn:van-gogh-museum/${_lidolinkResource.replaceAll(".jpg", "").sanitizeURI()}"
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
         _absent_ = true
         _input.lidolido * { _lidolido ->
            _lidolido.lidodescriptiveMetadata * { _lidodescriptiveMetadata ->
               _lidodescriptiveMetadata.lidoeventWrap * { _lidoeventWrap ->
                  _lidoeventWrap.lidoeventSet * { _lidoeventSet ->
                     _lidoeventSet.lidoevent * { _lidoevent ->
                        _lidoevent.lidoeventActor * { _lidoeventActor ->
                           'edm:Agent' (
                              'rdf:about' : {
                                 _lidoeventActor.lidoactorInRole * { _lidoactorInRole ->
                                    _lidoactorInRole.lidoactor * { _lidoactor ->
                                       if (_lidoactor.lidoactorID) {
                                          _lidoactor.lidoactorID * { _lidoactorID ->
                                             if (!"${_lidoactorID}".contains("ulan")) {
                                                "${_lidoactorID}"
                                             }
                                          }
                                          } else {
                                          name = _lidoactor.getValueNodes("lidoappellationValue").first()
                                          createEDMAgentUri(name).sanitizeURI()
                                       }
                                    }
                                 }
                              }
                           ) {
                              _absent_ = true
                              _lidoeventActor.lidoactorInRole * { _lidoactorInRole ->
                                 _lidoactorInRole.lidoactor * { _lidoactor ->
                                    _lidoactor.lidonameActorSet * { _lidonameActorSet ->
                                       _lidonameActorSet.lidoappellationValue * { _lidoappellationValue ->
                                          'skos:prefLabel' { _absent_ = false
                                             "${_lidoappellationValue}"
                                          }
                                       }
                                    }
                                 }
                              }
                              _absent_ = true
                              _lidoeventActor.lidoactorInRole * { _lidoactorInRole ->
                                 _lidoactorInRole.lidoroleActor * { _lidoroleActor ->
                                    _lidoroleActor.lidoterm * { _lidoterm ->
                                       'rdaGr2:professionOrOccupation' { _absent_ = false
                                          "${_lidoterm}"
                                       }
                                    }
                                 }
                              }
                              'owl:sameAs' (
                                 'rdf:resource' : {
                                    _lidoeventActor.lidoactorInRole * { _lidoactorInRole ->
                                       _lidoactorInRole.lidoactor * { _lidoactor ->
                                          _lidoactor.lidoactorID * { _lidoactorID ->
                                             if ("${_lidoactorID}".contains("edu/ulan/")) {
                                                "${_lidoactorID}"
                                             }
                                          }
                                       }
                                    }
                                 }
                              ) {
                                 // no node mappings
                              }
                              _absent_ = true
                              _lidoeventActor.lidoactorInRole * { _lidoactorInRole ->
                                 _lidoactorInRole.lidoactor * { _lidoactor ->
                                    _lidoactor.lidonameActorSet * { _lidonameActorSet ->
                                       _lidonameActorSet.lidoappellationValue * { _lidoappellationValue ->
                                          'nave:resourceSortOrder' { _absent_ = false
                                             "${_lidoeventSet.getValueNodes("lidoappellationValue").indexOf(_lidoappellationValue) + 1}"
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
         _absent_ = true
         _input.lidolido * { _lidolido ->
            _lidolido.lidodescriptiveMetadata * { _lidodescriptiveMetadata ->
               _lidodescriptiveMetadata.lidoobjectClassificationWrap * { _lidoobjectClassificationWrap ->
                  _lidoobjectClassificationWrap.lidoobjectWorkTypeWrap * { _lidoobjectWorkTypeWrap ->
                     _lidoobjectWorkTypeWrap.lidoobjectWorkType * { _lidoobjectWorkType ->
                        _lidoobjectWorkType.lidoconceptID * { _lidoconceptID ->
                           'skos:Concept' (
                              'rdf:about' : {
                                 "${_lidoconceptID}"
                              }
                           ) {
                              _absent_ = true
                              _lidoobjectWorkType.lidoterm * { _lidoterm ->
                                 'skos:prefLabel' { _absent_ = false
                                    "${_lidoterm}"
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
         _absent_ = true
         _input.lidolido * { _lidolido ->
            _lidolido.lidodescriptiveMetadata * { _lidodescriptiveMetadata ->
               _lidodescriptiveMetadata.lidoeventWrap * { _lidoeventWrap ->
                  _lidoeventWrap.lidoeventSet * { _lidoeventSet ->
                     _lidoeventSet.lidoevent * { _lidoevent ->
                        _lidoevent.lidoeventMaterialsTech * { _lidoeventMaterialsTech ->
                           _lidoeventMaterialsTech.lidotermMaterialsTech * { _lidotermMaterialsTech ->
                              _lidotermMaterialsTech.lidoconceptID * { _lidoconceptID ->
                                 'skos:Concept' (
                                    'rdf:about' : {
                                       "${_lidoconceptID}"
                                    }
                                 ) {
                                    _absent_ = true
                                    _lidotermMaterialsTech.lidoterm * { _lidoterm ->
                                       'skos:prefLabel' { _absent_ = false
                                          "${_lidoterm}"
                                       }
                                    }
                                 }
                              }
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

