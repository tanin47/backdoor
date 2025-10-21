export class FetchError extends Error {
  messages: string[]

  constructor(messages: string[]) {
    super()
    this.messages = messages
  }
}

const unknownError = new FetchError(['An unknown error occurred. Please contact your administrator.'])

export async function post(url: string, data: object): Promise<any> {
  const startTime = new Date().getTime();
  let resp;
  try {
    resp = await (fetch(url, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(data)
    }))
  } catch (e) {
    console.error(e)
    throw unknownError
  }

  const endTime = new Date().getTime();

  // if ((endTime - startTime) < 1000) {
  //   // Avoid flickering
  //   await new Promise(resolve => setTimeout(resolve, 1000 - (endTime - startTime)));
  // }

  if (resp.status >= 200 && resp.status < 300) {
    return await resp.json()
  } else if (resp.status >= 300 && resp.status < 400) {
    console.warn("The ajax request returns 3XX as a response. This is not supported.")
    throw unknownError
  } else if (resp.status >= 400 && resp.status < 500) {
    let json: any
    try {
      json = await resp.json()
    } catch (e) {
      throw unknownError
    }

    if (json.errors) {
      throw new FetchError(json.errors)
    } else {
      console.warn("The response status is 4XX, but json.errors doesn't exist. The server has a bug where it doesn't set json.errors. correctly.")
      throw unknownError
    }
  } else {
    throw unknownError
  }
}

export function invokeOnEnter(fn: () => void): (event: KeyboardEvent) => void {
  return (event: KeyboardEvent) => {
    if (event.key === 'Enter' && (event.target as HTMLElement).tagName.toLowerCase() !== 'textarea') {
      fn();
    }
  }
}
