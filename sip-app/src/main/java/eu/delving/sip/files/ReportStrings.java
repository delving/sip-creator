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

package eu.delving.sip.files;

/**
 * Replace regex due to unicode problems
 *
 *
 */
public class ReportStrings {
    private String[] parts;

    public ReportStrings(String... parts) {
        this.parts = parts;
    }

    public Match matcher(String line) {
        return new Match(line);
    }

    public class Match {
        private String line;
        private String[] group = new String[parts.length];

        public Match(String line) {
            this.line = line;
        }

        public boolean matches() {
            for (int walk = 0; walk < parts.length; walk++) {
                int where = line.indexOf(parts[walk]);
                if (where < 0) return false;
                if (walk < parts.length - 1) {
                    int whereNext = line.indexOf(parts[walk + 1], where);
                    if (whereNext < 0) return false;
                    group[walk] = line.substring(where + parts[walk].length(), whereNext);
                }
                else {
                    group[walk] = line.substring(where + parts[walk].length());
                }
            }
            return true;
        }

        public String group(int index) {
            if (index == 0) {
                return line;
            }
            else {
                index--;
            }
            if (index >= group.length) return "!";
            return group[index];
        }
    }

    public static ReportStrings START = new ReportStrings("<<", ",", ">>");
    public static ReportStrings END = new ReportStrings("<<>>");
    public static ReportStrings LINK = new ReportStrings("<<<",">>>");

    public static void main(String[] args) {
        Match match = START.matcher("<<5,VALID>>NF.1908-0211Æ1");
        if (match.matches()) {
            for (int walk=0; walk<5; walk++) {
                System.out.println("Group:"+match.group(walk));
            }
        }
    }
}
