export function useSingleTenant() {
  const config = useRuntimeConfig()
  const slug = computed(() => config.public.singleTenantSlug || '')
  const isSingleTenant = computed(() => !!slug.value)
  return { slug, isSingleTenant }
}
