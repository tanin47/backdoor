// @ts-expect-error defined globally
export const IS_LOCAL_DEV: string = window.IS_LOCAL_DEV;

export type Paradigm = 'CORE' | 'WEB' | 'DESKTOP';
// @ts-expect-error defined globally
export const PARADIGM: string = window.PARADIGM;


const NAMES = [
  "acacia",
  "alder",
  "ash",
  "aspen",
  "baobab",
  "beech",
  "birch",
  "cedar",
  "cherry",
  "chestnut",
  "cypress",
  "dogwood",
  "elm",
  "eucalyptus",
  "fir",
  "ginkgo",
  "hemlock",
  "hickory",
  "juniper",
  "larch",
  "linden",
  "locust",
  "magnolia",
  "maple",
  "mulberry",
  "oak",
  "palm",
  "pecan",
  "pine",
  "poplar",
  "redwood",
  "sequoia",
  "spruce",
  "sycamore",
  "walnut",
  "willow",
]

export function generateName(prefix: string, existingNames: string[], suffix: string = ""): string {
  const makeName = (name: string) => (prefix + name + suffix);

  const availableNames = NAMES.filter(name => !existingNames.includes(makeName(name)));
  if (availableNames.length === 0) {
    return generateName(prefix, existingNames, suffix + "_1")
  }
  return makeName(availableNames[0]);

}
