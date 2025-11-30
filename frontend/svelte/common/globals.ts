// @ts-expect-error defined globally
export const IS_LOCAL_DEV: boolean = window.IS_LOCAL_DEV;

// @ts-expect-error defined globally
export const APP_VERSION: boolean = window.APP_VERSION;

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


let fileSelectedCallback: ((content: string) => void) | null = null;

export function registerFileSelected(callback: (resp: any) => void) {
  fileSelectedCallback = callback;
}

// @ts-ignore
window.triggerFileSelected = (content: any) => {
  if (fileSelectedCallback) {
    fileSelectedCallback(content);
    fileSelectedCallback = null;
  }
}

export async function openFileDialog(isSaved: boolean): Promise<any> {
  return new Promise(async (resolve, reject) => {
    try {
      registerFileSelected((resp: any) => {
        resolve(resp);
        fileSelectedCallback = null;
      })

      const _resp = await (fetch('/select-file', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({isSaved}),
      }))
    } catch (e) {
      console.error(e)
      reject(e)
    }
  })
}
