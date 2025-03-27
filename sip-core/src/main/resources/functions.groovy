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
