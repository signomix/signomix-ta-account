package com.signomix.account.port.in;

import java.util.List;

import com.signomix.account.domain.TenantLogic;
import com.signomix.common.Tenant;
import com.signomix.common.User;
import com.signomix.common.db.IotDatabaseException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TenantPort {

    @Inject
    TenantLogic tenantLogic;

    public List<Tenant> getTenants(User user, Long organizationId, int limit, int offset) throws IotDatabaseException{
        return tenantLogic.getTenants(user, organizationId, limit, offset);
    }
    
    public Tenant getTenantById(User user, Integer tenantId) throws IotDatabaseException{
        return tenantLogic.getTenantById(user, tenantId);
    }
    
    public Tenant getTenantByRoot(User user, Long organizationId, String root) throws IotDatabaseException{
        return tenantLogic.getTenantByRoot(user, organizationId, root);
    }

    public void addTenant(User user, Tenant tenant) throws IotDatabaseException{
        tenantLogic.addTenant(user, tenant);
    }

    public void updateTenant(User user, Tenant tenant) throws IotDatabaseException{
        tenantLogic.updateTenant(user, tenant);
    }

    public void deleteTenant(User user, Integer tenantId) throws IotDatabaseException{
        tenantLogic.deleteTenant(user, tenantId);
    }
}
