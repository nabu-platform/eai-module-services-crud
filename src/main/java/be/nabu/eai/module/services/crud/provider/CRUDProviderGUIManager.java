package be.nabu.eai.module.services.crud.provider;

import java.io.IOException;
import java.util.List;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;

public class CRUDProviderGUIManager extends BaseJAXBGUIManager<CRUDProviderConfiguration, CRUDProviderArtifact> {

	public CRUDProviderGUIManager() {
		super("CRUD Provider", CRUDProviderArtifact.class, new CRUDProviderManager(), CRUDProviderConfiguration.class);
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return null;
	}

	@Override
	protected CRUDProviderArtifact newInstance(MainController controller, RepositoryEntry entry, Value<?>... values) throws IOException {
		return new CRUDProviderArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
	}

	@Override
	public String getCategory() {
		return "Providers";
	}
}
