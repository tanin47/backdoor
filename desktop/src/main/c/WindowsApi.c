#include <windows.h>
#include <stdio.h>
#include <stdbool.h>
#include <string.h>

char* getStringFromC(char *input) {
  char* str = strdup(input);
  return str;
}

__declspec(dllexport) void freeString(char* str) {
  free(str); // Free the memory allocated in C
}

__declspec(dllexport) char* openFileDialog(long hwnd, bool isSaved) {
  OPENFILENAME ofn;
  CHAR szFile[MAX_PATH];

  ZeroMemory(&ofn, sizeof(ofn));
  ofn.lStructSize = sizeof(ofn);
  ofn.hwndOwner = (HWND) (size_t) hwnd;
  ofn.lpstrFile = szFile;
  ofn.lpstrFile[0] = '\0';
  ofn.nMaxFile = sizeof(szFile);
  ofn.lpstrFilter = "All Files (*.*)\0";
  ofn.lpstrTitle = isSaved ? "Select a file to save" : "Select a file to open";
  ofn.Flags = isSaved ? OFN_OVERWRITEPROMPT : (OFN_PATHMUSTEXIST | OFN_FILEMUSTEXIST);

  printf("Opening the file dialog...\n");
  fflush(stdout);
  bool result = isSaved ? GetSaveFileName(&ofn) : GetOpenFileName(&ofn);

  // Display the Open dialog box
  if (result == TRUE) {
    printf("Selected file: %s\n", ofn.lpstrFile);
    fflush(stdout);
    return getStringFromC(ofn.lpstrFile);
  } else {
    printf("No file selected or an error occurred.\n");
    fflush(stdout);
    return NULL;
  }
}
