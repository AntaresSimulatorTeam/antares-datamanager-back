package com.rte_france.antares.datamanager_back.configuration.gaia;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LdapClientUtils {

    //Group name example: GSL_TCD_R1_GDP_APSU_AMIEN_COORDONNER => After split [7 elements]
    private static final int GROUP_NUMBER_OF_ELEMENTS = 7;

    public static String buildGroupNameLegacy(String groupName, boolean isDCH) {
        return isDCH ? "CN=" + groupName + PropertyManagement.BASE_GSL_GROUP_IN_APSU : groupName;
    }

    public static String buildGroupName(String groupName, boolean isDCH) {
        return isDCH ? buildLdapGroupRequest(groupName) : groupName;
    }
    public static List<String> buildGroupsNames(List<String> groupsNames, boolean isDCH){
        return groupsNames.stream().map(elt-> buildGroupName(elt, isDCH)).collect(Collectors.toList());
    }

    public static String buildLdapGroupRequest(String groupName){
        String[] groupElts = groupName.split("_");
        if(groupElts.length<GROUP_NUMBER_OF_ELEMENTS){
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("CN=").append(groupName)
                .append(",OU=").append(String.join("_", groupElts[2], groupElts[3], groupElts[4], groupElts[5])).append("_Acces")
                .append(",OU=").append(String.join("_", groupElts[2], groupElts[3], groupElts[4]))
                .append(",OU=").append(String.join("_", groupElts[2], groupElts[3]))
                .append(",OU=").append("Region_").append(groupElts[2].charAt(1))
                .append(",OU=Regions,DC=tcd,DC=local");
        return sb.toString();
    }

}
