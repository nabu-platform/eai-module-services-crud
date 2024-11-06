/*
* Copyright (C) 2020 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.eai.module.services.crud;

import be.nabu.eai.module.services.iface.DefinedServiceInterfaceArtifact;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.services.DefinedServiceInterfaceResolverFactory;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.DefinedServiceInterface;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.structure.Structure;

public class CRUDHook implements DefinedServiceInterface {

	private String id;
	private Structure input;
	private Structure output;

	public CRUDHook(String id, Structure input) {
		this.id = id;
		this.input = new Structure();
		this.input.setName("input");
		this.input.add(new ComplexElementImpl("data", input, this.input));
		this.output = new Structure();
		this.output.setName("output");
	}
	
	@Override
	public ComplexType getInputDefinition() {
		return input;
	}

	@Override
	public ComplexType getOutputDefinition() {
		return output;
	}

	@Override
	public ServiceInterface getParent() {
		return null;
	}

	@Override
	public String getId() {
		return id;
	}
	
	public void fire(ExecutionContext executionContext, ComplexContent input) throws ServiceException {
		DefinedServiceInterface hookIface = DefinedServiceInterfaceResolverFactory.getInstance().getResolver().resolve("be.nabu.eai.module.services.iface.HookListener.fire");
		for (DefinedService service : EAIResourceRepository.getInstance().getArtifacts(DefinedService.class)) {
			if (!(service instanceof DefinedServiceInterfaceArtifact) && !service.getId().equals(id)) {
				// if it is a generic hook listener, we run that
				if (isImplementation(service, hookIface)) {
					ServiceRuntime serviceRuntime = new ServiceRuntime(service, executionContext);
					ComplexContent hookInput = service.getServiceInterface().getInputDefinition().newInstance();
					hookInput.set("hook", id);
					hookInput.set("input", input);
					serviceRuntime.run(hookInput);
				}
				else if (isImplementation(service, this)) {
					ServiceRuntime serviceRuntime = new ServiceRuntime(service, executionContext);
					serviceRuntime.run(input);
				}
			}
		}
	}
	
	public static boolean isImplementation(Service service, ServiceInterface iface) {
		ServiceInterface serviceInterface = service.getServiceInterface();
		while (serviceInterface != null && !serviceInterface.equals(iface)) {
			// check on id as well
			if (serviceInterface instanceof Artifact && iface instanceof Artifact && ((Artifact) serviceInterface).getId().equals(((Artifact) iface).getId())) {
				break;
			}
			serviceInterface = serviceInterface.getParent();
		}
		return serviceInterface != null;
	}
}
