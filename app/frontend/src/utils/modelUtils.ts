import type { AiModel } from '../bridge'

export function getModelDisplayName(
  models: AiModel[],
  selectedId: string | undefined,
): string {
  if (!selectedId) return 'default'
  const found = models.find((m) => m.id === selectedId)
  return found ? found.displayName : selectedId
}
