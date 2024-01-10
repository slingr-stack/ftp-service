<table class="table" style="margin-top: 10px">
    <thead>
    <tr>
        <th>Title</th>
        <th>Last Updated</th>
        <th>Summary</th>
    </tr>
    </thead>
    <tbody>
    <tr>
        <td>Ftp service</td>
        <td>January 10, 2024</td>
        <td>Detailed description of the API of the Ftp service.</td>
    </tr>
    </tbody>
</table>

## Overview

The FTP service allows to fetch and upload files from/to and FTP server. Some of the features
supported by this service are:

- Supports protocols FTP, SFTP, FTPS
- Listening for new files, even in recursive folders
- Archiving of downloaded files
- Upload files

## Configuration

### Protocol

Protocol to use to connect to the server. Apart from plain FTP, this service also supports
more secure protocols like FTPS and SFTP.

### Host

The host of the FTP server. For example ftp.mycompany.com.

### Port

The port where the FTP server is listening (usually 21).

### Username

The username to login in the FTP server.

### Password

The password to login in the FTP server.

### File pattern

This is an Ant file pattern expression to filter which files will be downloaded from the input
folder. For example `*.csv`.

### Input folder

Folder to listen for files. If you leave this empty, the root folder will be used.

You need list, read and write access (files need to be archived).

### Recursive

If enabled the service will listener for new files inside the input folder in a recursive way,
which means it will go through all sub-folders.

### Archive folder

After files are downloaded by the service they will be moved so they aren't picked again. This
is the folder where files will be moved after being downloaded.

You need write access and if the recursive option is enabled, this must be outside of the input
folder.

### Archive grouping

To make archive more organized, this option allows to indicate how files will be grouped there.
If you don't have a lot of files, probably `Monthly` or `None` are fine, but if the amount of
files increases, you would probably want to start grouping them `Weekly` or even `Daily`.

### Output folder

This is the base folder where wiles will be uploaded. If empty, the root folder will be used.

Keep in mind that even if you specify a folder when uploading a file, this output folder will
be prepended.

## Javascript API

### Upload file

```js
app.endpoints.ftp.uploadFile(folder, fileId);
```

Uploads a file to the FTP server. The folder is appended to the output folder configured in the
service and it will be automatically created if it doesn't exist. Also the file will be overwritten
if it already exists.

Here is a sample:
 
```js
app.endpoints.ftp.uploadFile('folderA', record.field('logo').id());
```

## Events

### New file

This event happens when a new file is detected in the input folder (or any of its sub-folder if
recursive is enabled). You can handle the file like this:

```js
var document = sys.data.createEmpty('documents');
document.field('name').val(event.data.fileName);
document.field('file').val({
  id: event.data.fileId, 
  name: event.data.fileName,
  contentType: event.data.contentType
});
sys.data.save(document);
```



## About SLINGR

SLINGR is a low-code rapid application development platform that accelerates development, with robust architecture for integrations and executing custom workflows and automation.

[More info about SLINGR](https://slingr.io)

## License

This service is licensed under the Apache License 2.0. See the `LICENSE` file for more details.
