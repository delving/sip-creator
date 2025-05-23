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

package eu.delving.rdf;

/**
 * Enumerating the CRM Properties
 *
 *
 */

public enum CRMProperty {
    P1_is_identified_by,
    P1i_identifies,
    P2_has_type,
    P2i_is_type_of,
    P3_has_note,
    P3_parts_description,
    P4_has_time_span,
    P5_consists_of,
    P5i_forms_part_of,
    P7_took_place_at,
    P7i_witnessed,
    P8_took_place_on_or_within,
    P8i_witnessed,
    P9_consists_of,
    P9i_forms_part_of,
    P10_falls_within,
    P11_had_participant,
    P11_participated_in,
    P11i_participated_in,
    P12_occurred_in_the_presence_of,
    P12i_was_present_at,
    P13_destroyed,
    P13i_was_destroyed_by,
    P14_carried_out_by,
    P14i_performed,
    P15_was_influenced_by,
    P15i_influenced,
    P16_used_specific_object,
    P16i_was_used_for,
    P17_was_motivated_by,
    P17i_motivated,
    P19_was_intended_use_of,
    P19i_was_made_for,
    P21_had_general_purpose,
    P21i_was_purpose_of,
    P22_transferred_title_to,
    P22i_acquired_title_through,
    P23_transferred_title_from,
    P23i_surrendered_title_through,
    P24_transferred_title_of,
    P24i_changed_ownership_through,
    P25_moved,
    P25i_moved_by,
    P26_moved_to,
    P26i_was_destination_of,
    P27_moved_from,
    P27i_was_origin_of,
    P28_custody_surrendered_by,
    P28i_surrendered_custody_through,
    P29_custody_received_by,
    P29i_received_custody_through,
    P31_has_modified,
    P31i_was_modified_by,
    P32_used_general_technique,
    P32i_was_technique_of,
    P33_used_specific_technique,
    P33i_was_used_by,
    P34_concerned,
    P34i_was_assessed_by,
    P35_has_identified,
    P35i_was_identified_by,
    P37_assigned,
    P37i_was_assigned_by,
    P38_deassigned,
    P38i_was_deassigned_by,
    P39_measured,
    P39i_was_measured_by,
    P41_classified,
    P41i_was_classified_by,
    P42_assigned,
    P42i_was_assigned_by,
    P43_has_dimension,
    P43i_is_dimension_of,
    P44_has_condition,
    P44i_is_condition_of,
    P45_consists_of,
    P45i_is_incorporated_in,
    P46_is_composed_of,
    P46i_forms_part_of,
    P48_has_preferred_identifier,
    P48i_is_preferred_identifier_of,
    P49_has_former_or_current_keeper,
    P49i_is_former_or_current_keeper_of,
    P50_has_current_keeper,
    P51_has_former_or_current_owner,
    P51i_is_former_or_current_owner_of,
    P52_has_current_owner,
    P52i_is_current_owner_of,
    P53_has_former_or_current_location,
    P53i_is_former_or_current_location_of,
    P54_has_current_permanent_location,
    P54i_is_current_permanent_location_of,
    P55_has_current_location,
    P55i_currently_holds,
    P56_bears_feature,
    P56i_is_found_on,
    P57_has_number_of_parts,
    P58_has_section_definition,
    P58i_defines_section,
    P59_has_section,
    P59i_is_located_on_or_within,
    P62_depicts,
    P62i_is_depicted_by,
    P65_shows_visual_item,
    P65i_is_shown_by,
    P67_refers_to,
    P67i_is_referred_to_by,
    P68_foresees_use_of,
    P68i_use_foreseen_by,
    P69_is_associated_with,
    P70_is_documented_in,
    P71_lists,
    P71i_is_listed_in,
    P72_has_language,
    P72i_is_language_of,
    P73_has_translation,
    P73i_is_translation_of,
    P74_has_current_or_former_residence,
    P74i_is_current_or_former_residence_of,
    P75_possesses,
    P75i_is_possessed_by,
    P76_has_contact_point,
    P76i_provides_access_to,
    P78_is_identified_by,
    P78i_identifies,
    P79_beginning_is_qualified_by,
    P80_end_is_qualified_by,
    P81_ongoing_throughout,
    P82_at_some_time_within,
    P83_had_at_least_duration,
    P83i_was_minimum_duration_of,
    P84_had_at_most_duration,
    P84i_was_maximum_duration_of,
    P86_falls_within,
    P86i_contains,
    P87_is_identified_by,
    P87i_identifies,
    P88_consists_of,
    P88i_forms_part_of,
    P89_falls_within,
    P89i_contains,
    P91_has_unit,
    P91i_is_unit_of,
    P92_brought_into_existence,
    P92i_was_brought_into_existence_by,
    P93_took_out_of_existence,
    P93i_was_taken_out_of_existence_by,
    P94_has_created,
    P94i_was_created_by,
    P95_has_formed,
    P95i_was_formed_by,
    P96_by_mother,
    P96i_gave_birth,
    P97_from_father,
    P97i_was_father_for,
    P98_brought_into_life,
    P98i_was_born,
    P99_dissolved,
    P99i_was_dissolved_by,
    P104_is_subject_to,
    P107_is_current_or_former_member_of,
    P108_was_produced_by,
    P111_added,
    P111i_was_added_by,
    P112_diminished,
    P112i_was_diminished_by,
    P113_removed,
    P113i_was_removed_by,
    P114_is_equal_in_time_to,
    P115_finishes,
    P115i_is_finished_by,
    P116_starts,
    P116i_is_started_by,
    P117_occurs_during,
    P117i_includes,
    P118_overlaps_in_time_with,
    P118i_is_overlapped_in_time_by,
    P119_meets_in_time_with,
    P119i_is_met_in_time_by,
    P121_overlaps_with,
    P122_borders_with,
    P123_resulted_in,
    P123i_resulted_from,
    P124_transformed,
    P124i_was_transformed_by,
    P125_used_object_of_type,
    P125i_was_type_of_object_used_in,
    P126_employed,
    P126i_was_employed_in,
    P127_has_broader_term,
    P127i_has_narrower_term,
    P128_carries,
    P128i_is_carried_by,
    P129_is_about,
    P129i_is_subject_of,
    P131_is_identified_by,
    P131i_identifies,
    P132_overlaps_with,
    P133_is_separated_from,
    P134_continued,
    P134i_was_continued_by,
    P135_created_type,
    P135i_was_created_by,
    P136_was_based_on,
    P136i_supported_type_creation,
    P137_exemplifies,
    P137i_is_exemplified_by,
    P138_represents,
    P138i_has_representation,
    P139_has_alternative_form,
    P141_assigned,
    P141i_was_assigned_by,
    P142_used_constituent,
    P142i_was_used_in,
    P143_joined,
    P143i_was_joined_by,
    P144_joined_with,
    P144i_gained_member_by,
    P145_separated,
    P145i_left_by,
    P146_separated_from,
    P146i_lost_member_by,
    P147_curated,
    P147i_was_curated_by,
    P148_has_component,
    P148i_is_component_of,
    P149_is_identified_by,
    P149i_identifies,
}
