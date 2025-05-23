package com.signomix.account.port.in;

import java.util.List;

import com.signomix.account.domain.OrganizationLogic;
import com.signomix.common.Organization;
import com.signomix.common.User;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OrganizationPort {

    @Inject
    OrganizationLogic organizationLogic;

    public Organization getOrganization(User user, Integer id){
        return organizationLogic.getOrganization(user, id);
    }

    public List<Organization> getOrganizations(User user, Integer limit, Integer offset){
        return organizationLogic.getOrganizations(user, limit, offset);
    }

    public void addOrganization(User user, Organization organization){
        organizationLogic.addOrganization(user, organization);
    }

    public void updateOrganization(User user, Organization organization){
        organizationLogic.updateOrganization(user, organization);
    }

    public void deleteOrganization(User user, Integer organizationId){
        organizationLogic.deleteOrganization(user, organizationId);
    }   
    
}
