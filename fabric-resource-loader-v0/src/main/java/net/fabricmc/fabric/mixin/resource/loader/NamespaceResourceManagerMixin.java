/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.mixin.resource.loader;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.minecraft.resource.NamespaceResourceManager;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.impl.resource.loader.GroupResourcePack;

/**
 * Patches getAllResources and method_41265 to work with GroupResourcePack.
 */
@Mixin(NamespaceResourceManager.class)
public class NamespaceResourceManagerMixin {
	private final ThreadLocal<List<NamespaceResourceManager.Entry>> fabric$getAllResources$resources = new ThreadLocal<>();

	@Inject(method = "getAllResources",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/resource/NamespaceResourceManager;getMetadataPath(Lnet/minecraft/util/Identifier;)Lnet/minecraft/util/Identifier;"),
			locals = LocalCapture.CAPTURE_FAILHARD)
	private void onGetAllResources(Identifier id, CallbackInfoReturnable<List<Resource>> cir, List<NamespaceResourceManager.Entry> resources) {
		this.fabric$getAllResources$resources.set(resources);
	}

	@Redirect(method = "getAllResources",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/resource/ResourcePack;contains(Lnet/minecraft/resource/ResourceType;Lnet/minecraft/util/Identifier;)Z"))
	private boolean onResourceAdd(ResourcePack pack, ResourceType type, Identifier id) {
		if (pack instanceof GroupResourcePack) {
			((GroupResourcePack) pack).appendResources((NamespaceResourceManagerAccessor) this, id, this.fabric$getAllResources$resources.get());

			return false;
		}

		return pack.contains(type, id);
	}

	@Redirect(method = "findAndAdd",
			at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"),
			allow = 1)
	private boolean onResourceAdd(List<NamespaceResourceManager.Entry> entries, Object entryObject) {
		// Required due to type erasure of List.add
		NamespaceResourceManager.Entry entry = (NamespaceResourceManager.Entry) entryObject;
		ResourcePack pack = entry.pack;

		if (pack instanceof GroupResourcePack grp) {
			grp.appendResources((NamespaceResourceManagerAccessor) this, entry.id, entries);
			return true;
		}

		return entries.add(entry);
	}
}
