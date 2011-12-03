/*
 * Copyright 2011 DELVING BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.groovy;

/**
 * Discard a record explicitly for some good reason by having the
 * Groovy code throw this.  This is done in Groovy code by making
 * a call on a boolean object.
 *
 * (condition).discard('condition is met, throw this record out')
 *
 * like
 *
 * (size > 3).discard("List is too large at ${size}!")
 *
 * <code>
 *   From MappingCategory.groovy:
 *
 *   static void discard(Boolean condition, String why) {
 *       if (condition) throw new DiscardRecordException(why)
 *   }
 * </code>
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class DiscardRecordException extends RuntimeException {

    public DiscardRecordException(String reason) {
        super("Record Discarded because: "+reason);
    }
}
